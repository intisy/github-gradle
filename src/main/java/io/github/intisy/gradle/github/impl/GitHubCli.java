package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.Logger;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes GitHub REST calls through the local {@code gh} CLI ({@code gh api ...}) instead of direct HTTP.
 *
 * <p>The CLI supplies its own authentication and higher (authenticated) rate limits, so no access token
 * needs to be configured. Each call is adapted back into an OkHttp {@link Response} so the surrounding
 * response-handling and JSON parsing in {@link GitHub} stay unchanged.
 */
public class GitHubCli {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    /** Extracts the HTTP status code from a {@code gh} error line such as "gh: Not Found (HTTP 404)". */
    private static final Pattern HTTP_CODE = Pattern.compile("\\(HTTP (\\d{3})\\)");

    private final Logger logger;
    private Boolean available;

    /**
     * @param logger the logger for diagnostics.
     */
    public GitHubCli(Logger logger) {
        this.logger = logger;
    }

    /**
     * Checks whether the {@code gh} CLI is installed and runnable. The result is probed once and cached.
     *
     * @return true if {@code gh --version} succeeds.
     */
    public boolean isAvailable() {
        if (available == null) {
            available = probe();
            if (!available) {
                logger.warn("github.cli.enabled is true but the 'gh' CLI was not found on PATH.");
            }
        }
        return available;
    }

    /**
     * Runs {@code gh --version} to detect the CLI.
     *
     * @return true if the process ran and exited successfully.
     */
    private boolean probe() {
        try {
            Process process = new ProcessBuilder("gh", "--version").redirectErrorStream(true).start();
            readFully(process.getInputStream());
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Performs a GitHub REST request via {@code gh api} and adapts the result into an OkHttp {@link Response}.
     *
     * @param url      the full GitHub API URL (also accepted as a bare path by {@code gh api}).
     * @param method   the HTTP method, e.g. {@code "GET"} or {@code "POST"}.
     * @param jsonBody the JSON request body for writes, or null for reads.
     * @return a synthetic {@link Response} carrying the CLI's status code and body.
     * @throws IOException if the CLI process cannot be started or its I/O fails.
     */
    public Response request(String url, String method, String jsonBody) throws IOException {
        List<String> command = buildCommand(url, method, jsonBody != null);
        logger.debug("Invoking gh CLI: " + String.join(" ", command));

        Process process = new ProcessBuilder(command).start();
        StreamReader errorReader = new StreamReader(process.getErrorStream());
        errorReader.start();

        if (jsonBody != null) {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        byte[] body = readFully(process.getInputStream());
        int exitCode;
        try {
            exitCode = process.waitFor();
            errorReader.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for gh CLI.", e);
        }

        String stderr = new String(errorReader.bytes(), StandardCharsets.UTF_8);
        int code = exitCode == 0 ? 200 : parseHttpCode(stderr);
        String message = exitCode == 0 ? "OK" : stderr.trim();

        return buildResponse(url, code, message, body, stderr);
    }

    /**
     * Assembles the {@code gh api} command line.
     *
     * @param url     the API URL or path.
     * @param method  the HTTP method.
     * @param hasBody whether a request body is written to stdin.
     * @return the command and its arguments.
     */
    private List<String> buildCommand(String url, String method, boolean hasBody) {
        List<String> command = new ArrayList<String>();
        command.add("gh");
        command.add("api");
        command.add(url);
        command.add("--method");
        command.add(method);
        command.add("-H");
        command.add("Accept: application/vnd.github+json");
        command.add("-H");
        command.add("X-GitHub-Api-Version: 2022-11-28");
        if (hasBody) {
            command.add("--input");
            command.add("-");
        }
        return command;
    }

    /**
     * Builds an OkHttp {@link Response} from the CLI output. When the CLI reports a rate limit, an
     * {@code X-RateLimit-Remaining: 0} header is attached so callers detect it exactly as for HTTP.
     *
     * @param url     the requested URL.
     * @param code    the resolved HTTP status code.
     * @param message the status message.
     * @param body    the response body bytes.
     * @param stderr  the CLI standard error, inspected for rate-limit wording.
     * @return the synthetic response.
     */
    private Response buildResponse(String url, int code, String message, byte[] body, String stderr) {
        String bodyText = new String(body, StandardCharsets.UTF_8);
        Response.Builder builder = new Response.Builder()
                .request(new Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message == null || message.isEmpty() ? "OK" : message)
                .body(ResponseBody.create(bodyText, JSON));
        if (isRateLimited(code, stderr, bodyText)) {
            builder.addHeader("X-RateLimit-Remaining", "0");
        }
        return builder.build();
    }

    /**
     * @param code     the HTTP status code.
     * @param stderr   the CLI standard error.
     * @param bodyText the response body.
     * @return true if the response indicates a GitHub rate limit.
     */
    private boolean isRateLimited(int code, String stderr, String bodyText) {
        if (code != 403 && code != 429) {
            return false;
        }
        String haystack = (stderr + " " + bodyText).toLowerCase();
        return haystack.contains("rate limit");
    }

    /**
     * Parses the HTTP status code from the CLI's error output, defaulting to 500 when absent.
     *
     * @param stderr the CLI standard error.
     * @return the parsed status code, or 500 if none is present.
     */
    private static int parseHttpCode(String stderr) {
        Matcher matcher = HTTP_CODE.matcher(stderr);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 500;
    }

    /**
     * Reads an input stream fully into a byte array, closing it afterwards.
     *
     * @param input the stream to drain.
     * @return the stream contents.
     * @throws IOException if reading fails.
     */
    private static byte[] readFully(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        try (InputStream in = input) {
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        }
        return buffer.toByteArray();
    }

    /**
     * Drains a stream on its own thread to avoid deadlocking when both stdout and stderr fill their buffers.
     */
    private static final class StreamReader extends Thread {
        private final InputStream input;
        private byte[] result = new byte[0];

        StreamReader(InputStream input) {
            this.input = input;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                result = readFully(input);
            } catch (IOException e) {
                result = new byte[0];
            }
        }

        byte[] bytes() {
            return result;
        }
    }
}
