package io.github.intisy.gradle.github.impl;

import com.sun.net.httpserver.HttpServer;
import io.github.intisy.gradle.github.api.capability.Downloads;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.impl.download.UrlDownloads;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link UrlDownloads} entirely offline: a canned {@link Interceptor} stands in for the
 * network, so no test here ever opens a socket, with one exception:
 * {@link #a302RedirectIsNeverFollowed} needs OkHttp's real redirect machinery, which sits below
 * where an application {@link Interceptor} can observe it (an interceptor that returns a
 * response directly never reaches it), so it drives a real {@link HttpServer} bound to the
 * loopback address only, torn down at the end of the test.
 *
 * <p>{@link #headerValueNeverAppearsInLogsOrExceptionMessages} is the load-bearing test in this
 * class: it supplies a header carrying a recognisable sentinel value across a success path, an
 * HTTP-error path, and a sha256-mismatch path, and asserts the sentinel never surfaces in a log
 * line, a thrown message, or a cache file name, while confirming (via the interceptor) that the
 * header genuinely was sent.
 */
public class TestUrlDownloads {

    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    @Test
    public void successfulDownloadIsCachedUnderAHashOfTheUrl(@TempDir File cacheDir) throws IOException {
        byte[] jarBytes = "jar-content".getBytes(StandardCharsets.UTF_8);
        CapturingLogger logger = new CapturingLogger();
        RecordingInterceptor interceptor = new RecordingInterceptor();
        OkHttpClient client = clientReturning(interceptor, 200, jarBytes);
        Downloads downloads = new UrlDownloads(client, logger, cacheDir);

        File jar = downloads.download("https://example.com/foo.jar", null, null);

        assertTrue(jar.isFile());
        assertArrayEquals(jarBytes, Files.readAllBytes(jar.toPath()));
        assertEquals(sha256Hex("https://example.com/foo.jar"), stripExtension(jar.getName()));
    }

    @Test
    public void secondCallForTheSameUrlUsesTheCacheWithoutASecondRequest(@TempDir File cacheDir) throws IOException {
        byte[] jarBytes = "jar-content".getBytes(StandardCharsets.UTF_8);
        CapturingLogger logger = new CapturingLogger();
        RecordingInterceptor interceptor = new RecordingInterceptor();
        OkHttpClient client = clientReturning(interceptor, 200, jarBytes);
        Downloads downloads = new UrlDownloads(client, logger, cacheDir);

        File first = downloads.download("https://example.com/foo.jar", null, null);
        File second = downloads.download("https://example.com/foo.jar", null, null);

        assertEquals(first, second);
        assertEquals(1, interceptor.requests.size());
    }

    @Test
    public void matchingSha256Succeeds(@TempDir File cacheDir) throws IOException {
        byte[] jarBytes = "jar-content".getBytes(StandardCharsets.UTF_8);
        CapturingLogger logger = new CapturingLogger();
        OkHttpClient client = clientReturning(new RecordingInterceptor(), 200, jarBytes);
        Downloads downloads = new UrlDownloads(client, logger, cacheDir);

        File jar = downloads.download("https://example.com/foo.jar", null, sha256Hex(jarBytes));

        assertTrue(jar.isFile());
    }

    @Test
    public void mismatchedSha256FailsLoudlyAndLeavesNoCachedFile(@TempDir File cacheDir) {
        byte[] jarBytes = "jar-content".getBytes(StandardCharsets.UTF_8);
        CapturingLogger logger = new CapturingLogger();
        OkHttpClient client = clientReturning(new RecordingInterceptor(), 200, jarBytes);
        Downloads downloads = new UrlDownloads(client, logger, cacheDir);
        String wrongSha256 = sha256Hex("not the jar content");

        IOException thrown = assertThrows(IOException.class,
                () -> downloads.download("https://example.com/foo.jar", null, wrongSha256));

        assertTrue(thrown.getMessage().contains("sha256 mismatch"));
        assertTrue(thrown.getMessage().contains(wrongSha256));
        File[] cachedEntries = cacheDir.listFiles((dir, name) -> name.endsWith(".jar"));
        assertEquals(0, cachedEntries.length, "a mismatched download must not be cached");
    }

    @Test
    public void httpErrorPropagatesAndLeavesNoCachedFile(@TempDir File cacheDir) {
        CapturingLogger logger = new CapturingLogger();
        OkHttpClient client = clientReturning(new RecordingInterceptor(), 500, "server error".getBytes(StandardCharsets.UTF_8));
        Downloads downloads = new UrlDownloads(client, logger, cacheDir);

        IOException thrown = assertThrows(IOException.class,
                () -> downloads.download("https://example.com/foo.jar", null, null));

        assertTrue(thrown.getMessage().contains("500"));
        File[] cachedEntries = cacheDir.listFiles((dir, name) -> name.endsWith(".jar"));
        assertEquals(0, cachedEntries.length);
    }

    /**
     * Important 6's regression test. OkHttp follows a redirect by default, forwarding any header
     * that is not {@code Authorization} to whatever host answers the 3xx, even across hosts. This
     * test spins up a genuine local {@link HttpServer} (loopback only) whose {@code /original.jar}
     * context returns a 302 pointing at its own {@code /redirected.jar} context, and asserts the
     * redirect target is never reached at all, which an application {@link Interceptor} cannot
     * demonstrate because it short-circuits OkHttp's redirect-following interceptor entirely.
     */
    @Test
    public void a302RedirectIsNeverFollowed(@TempDir File cacheDir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirectTargetHits = new AtomicInteger();
        int port = server.getAddress().getPort();
        server.createContext("/original.jar", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected.jar");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected.jar", exchange -> {
            redirectTargetHits.incrementAndGet();
            byte[] body = "jar-content".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            CapturingLogger logger = new CapturingLogger();
            Downloads downloads = new UrlDownloads(new OkHttpClient(), logger, cacheDir);

            IOException thrown = assertThrows(IOException.class,
                    () -> downloads.download("http://127.0.0.1:" + port + "/original.jar", null, null));

            assertTrue(thrown.getMessage().contains("302"), "message: " + thrown.getMessage());
            assertEquals(0, redirectTargetHits.get(), "the redirect target must never be reached");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void plainHttpUrlWithHeadersLogsAWarning(@TempDir File cacheDir) {
        CapturingLogger logger = new CapturingLogger();
        OkHttpClient client = clientReturning(new RecordingInterceptor(), 200, "jar-content".getBytes(StandardCharsets.UTF_8));
        Downloads downloads = new UrlDownloads(client, logger, cacheDir);
        Map<String, String> headers = Collections.singletonMap("X-Api-Key", "some-token");

        assertDoesNotThrow(() -> downloads.download("http://example.com/foo.jar", headers, null));

        assertTrue(logger.warnings.stream().anyMatch(message -> message.contains("http://")),
                "expected a warning naming the plain-http URL; got: " + logger.warnings);
    }

    @Test
    public void plainHttpUrlWithoutHeadersLogsNoWarning(@TempDir File cacheDir) {
        CapturingLogger logger = new CapturingLogger();
        OkHttpClient client = clientReturning(new RecordingInterceptor(), 200, "jar-content".getBytes(StandardCharsets.UTF_8));
        Downloads downloads = new UrlDownloads(client, logger, cacheDir);

        assertDoesNotThrow(() -> downloads.download("http://example.com/foo.jar", null, null));

        assertTrue(logger.warnings.isEmpty());
    }

    @Test
    public void headerValueNeverAppearsInLogsOrExceptionMessages(@TempDir File cacheDir) throws IOException {
        String sentinel = "SENTINEL-3f9a7c21-do-not-leak-me";
        Map<String, String> headers = Collections.singletonMap("X-Api-Key", sentinel);
        byte[] jarBytes = "jar-content".getBytes(StandardCharsets.UTF_8);

        CapturingLogger logger = new CapturingLogger();
        RecordingInterceptor interceptor = new RecordingInterceptor();
        OkHttpClient successClient = clientReturning(interceptor, 200, jarBytes);
        Downloads successDownloads = new UrlDownloads(successClient, logger, cacheDir);

        File jar = successDownloads.download("https://example.com/secret-1.jar", headers, null);

        assertTrue(jar.isFile());
        assertEquals(sentinel, interceptor.requests.get(0).header("X-Api-Key"),
                "the header must genuinely have been sent, or this test would pass vacuously");

        CapturingLogger errorLogger = new CapturingLogger();
        OkHttpClient errorClient = clientReturning(new RecordingInterceptor(), 500, "boom".getBytes(StandardCharsets.UTF_8));
        Downloads errorDownloads = new UrlDownloads(errorClient, errorLogger, cacheDir);
        IOException httpError = assertThrows(IOException.class,
                () -> errorDownloads.download("https://example.com/secret-2.jar", headers, null));

        CapturingLogger mismatchLogger = new CapturingLogger();
        OkHttpClient mismatchClient = clientReturning(new RecordingInterceptor(), 200, jarBytes);
        Downloads mismatchDownloads = new UrlDownloads(mismatchClient, mismatchLogger, cacheDir);
        IOException shaError = assertThrows(IOException.class,
                () -> mismatchDownloads.download("https://example.com/secret-3.jar", headers, sha256Hex("wrong")));

        List<String> allCapturedText = new ArrayList<>();
        allCapturedText.addAll(logger.messages);
        allCapturedText.addAll(errorLogger.messages);
        allCapturedText.addAll(mismatchLogger.messages);
        allCapturedText.addAll(messagesOf(httpError));
        allCapturedText.addAll(messagesOf(shaError));
        for (File cached : cacheDirEntries(cacheDir)) {
            allCapturedText.add(cached.getName());
        }

        for (String text : allCapturedText) {
            assertFalse(text != null && text.contains(sentinel),
                    "captured text must never contain the header sentinel, but found it in: " + text);
        }
    }

    /**
     * OkHttp's {@code Headers.Builder} rejects a value containing a control character (a trailing
     * newline, the ordinary shape of {@code file("token.txt").text} in Groovy) and embeds the raw
     * value in its own {@code IllegalArgumentException} message, redacting only a fixed set of
     * header names ({@code Authorization}, {@code Cookie}, {@code Proxy-Authorization}, {@code
     * Set-Cookie}). {@code X-Api-Key} is deliberately not one of them, so this test can actually
     * fail if {@link UrlDownloads} does not guard the call itself.
     */
    @Test
    public void malformedHeaderValueDoesNotLeakIntoAnyExceptionMessage(@TempDir File cacheDir) {
        String sentinel = "SENTINEL-TOKEN-do-not-leak-8f3a1c";
        Map<String, String> headers = Collections.singletonMap("X-Api-Key", sentinel + "\n");
        CapturingLogger logger = new CapturingLogger();
        OkHttpClient client = clientReturning(new RecordingInterceptor(), 200, "jar-content".getBytes(StandardCharsets.UTF_8));
        Downloads downloads = new UrlDownloads(client, logger, cacheDir);

        Exception thrown = assertThrows(Exception.class,
                () -> downloads.download("https://example.com/malformed.jar", headers, null));

        assertTrue(thrown instanceof IOException,
                "a malformed header value must surface as a clean IOException, not a raw IllegalArgumentException");
        assertTrue(thrown.getMessage() != null && thrown.getMessage().contains("X-Api-Key"),
                "the exception should still name which header key was invalid");

        List<String> allText = new ArrayList<>();
        allText.addAll(messagesOf(thrown));
        allText.addAll(logger.messages);
        for (String text : allText) {
            assertFalse(text != null && text.contains(sentinel),
                    "the header value must never appear in an exception message or log line, but found it in: " + text);
        }
    }

    /**
     * A presigned or {@code ?token=}-style download URL, and {@code https://user:token@host/...},
     * are both ordinary shapes that themselves carry a credential, entirely separate from any
     * header. This test drives a success path and a failure path with such URLs and asserts the
     * embedded token never surfaces.
     */
    @Test
    public void credentialEmbeddedInTheUrlItselfNeverAppearsInLogsOrExceptionMessages(@TempDir File cacheDir) throws IOException {
        String sentinel = "SENTINEL-URL-TOKEN-2b7e91";
        String queryUrl = "https://example.com/secret-4.jar?token=" + sentinel;
        String userinfoUrl = "https://user:" + sentinel + "@example.com/secret-5.jar";

        CapturingLogger successLogger = new CapturingLogger();
        OkHttpClient successClient = clientReturning(new RecordingInterceptor(), 200, "jar-content".getBytes(StandardCharsets.UTF_8));
        Downloads successDownloads = new UrlDownloads(successClient, successLogger, cacheDir);
        assertTrue(successDownloads.download(queryUrl, null, null).isFile());

        CapturingLogger errorLogger = new CapturingLogger();
        OkHttpClient errorClient = clientReturning(new RecordingInterceptor(), 500, "boom".getBytes(StandardCharsets.UTF_8));
        Downloads errorDownloads = new UrlDownloads(errorClient, errorLogger, cacheDir);
        IOException thrown = assertThrows(IOException.class, () -> errorDownloads.download(userinfoUrl, null, null));

        List<String> allText = new ArrayList<>();
        allText.addAll(successLogger.messages);
        allText.addAll(errorLogger.messages);
        allText.addAll(messagesOf(thrown));
        for (File cached : cacheDirEntries(cacheDir)) {
            allText.add(cached.getName());
        }

        for (String text : allText) {
            assertFalse(text != null && text.contains(sentinel),
                    "a credential embedded in the URL itself must never appear in captured text, but found it in: " + text);
        }
    }

    private static List<String> messagesOf(Throwable throwable) {
        List<String> messages = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            messages.add(current.getMessage());
            messages.add(String.valueOf(current));
            current = current.getCause();
        }
        return messages;
    }

    private static List<File> cacheDirEntries(File cacheDir) {
        File[] entries = cacheDir.listFiles();
        return entries == null ? Collections.<File>emptyList() : java.util.Arrays.asList(entries);
    }

    private static OkHttpClient clientReturning(Interceptor recorder, int status, byte[] body) {
        return new OkHttpClient.Builder()
                .addInterceptor(recorder)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        return new Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(status)
                                .message(status == 200 ? "OK" : "Error")
                                .body(ResponseBody.create(body, OCTET_STREAM))
                                .build();
                    }
                })
                .build();
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class RecordingInterceptor implements Interceptor {
        private final List<Request> requests = new ArrayList<>();

        @Override
        public Response intercept(Chain chain) throws IOException {
            requests.add(chain.request());
            return chain.proceed(chain.request());
        }
    }

    private static final class CapturingLogger implements GitHubLogger {
        private final List<String> messages = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }

        @Override
        public void error(String message) {
            messages.add(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            messages.add(message);
            messages.addAll(messagesOf(throwable));
        }

        @Override
        public void debug(String message) {
            messages.add(message);
        }

        @Override
        public void warn(String message) {
            messages.add(message);
            warnings.add(message);
        }
    }
}
