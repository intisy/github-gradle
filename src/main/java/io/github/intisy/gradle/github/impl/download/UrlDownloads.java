package io.github.intisy.gradle.github.impl.download;

import io.github.intisy.gradle.github.api.capability.Downloads;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.utils.UrlDigest;
import io.github.intisy.gradle.github.utils.UrlRedaction;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Downloads a jar from an arbitrary HTTP(S) URL, caching it under a directory keyed by a hash of
 * {@code jarUrl}, and verifying an optional expected SHA-256 of the downloaded content.
 *
 * @implNote Header values are user secrets, and a URL can carry one too (a presigned or {@code
 * ?token=} URL, or {@code https://user:token@host/...}). This class never writes a header value
 * anywhere but the outgoing {@link okhttp3.Request}, and every log line and exception message
 * that names {@code jarUrl} runs it through {@link UrlRedaction#redact} first, stripping userinfo
 * and the query string. The cache file name is a hash of {@code jarUrl} alone (via {@link
 * UrlDigest}), never the URL itself.
 */
public final class UrlDownloads implements Downloads {
    private final OkHttpClient httpClient;
    private final GitHubLogger logger;
    private final File cacheDir;

    /**
     * @param httpClient issues the download request; injected so tests can intercept it without a
     *                    real network call.
     * @param logger receives diagnostic output.
     * @param cacheDir the directory downloaded jars are cached under, keyed by a hash of the URL.
     */
    public UrlDownloads(OkHttpClient httpClient, GitHubLogger logger, File cacheDir) {
        this.httpClient = httpClient;
        this.logger = logger;
        this.cacheDir = cacheDir;
    }

    @Override
    public File download(String jarUrl, Map<String, String> headers, String sha256) throws IOException {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + cacheDir.getAbsolutePath());
        }
        String redactedUrl = UrlRedaction.redact(jarUrl);

        File cachedJar = new File(cacheDir, UrlDigest.sha256Hex(jarUrl) + ".jar");
        if (cachedJar.isFile()) {
            logger.debug("Using cached download: " + cachedJar.getName());
            verifyOrThrow(cachedJar, sha256, redactedUrl);
            return cachedJar;
        }

        Request.Builder requestBuilder = new Request.Builder().url(jarUrl);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                addHeaderOrThrow(requestBuilder, header.getKey(), header.getValue());
            }
        }
        logger.log("Downloading jar from " + redactedUrl);

        File tempFile = File.createTempFile("download-", ".tmp", cacheDir);
        try {
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to download " + redactedUrl + ": HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty response body when downloading " + redactedUrl + ".");
                }
                streamToFile(body.byteStream(), tempFile);
            }
            verifyOrThrow(tempFile, sha256, redactedUrl);
            Files.move(tempFile.toPath(), cachedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            logger.error("Failed to download " + redactedUrl + ": " + e.getMessage(), e);
            throw e;
        }
        return cachedJar;
    }

    /**
     * @implNote OkHttp's own {@code Headers.Builder} rejects a value containing a character
     * outside {@code \t} and the printable ASCII range by throwing an {@link
     * IllegalArgumentException} whose message embeds the raw value, unredacted for any header
     * name other than a fixed list ({@code Authorization}, {@code Cookie}, {@code
     * Proxy-Authorization}, {@code Set-Cookie}). This method catches that exception without
     * touching its message or attaching it as a cause, and throws a fresh {@link IOException}
     * naming only the header key.
     */
    private static void addHeaderOrThrow(Request.Builder requestBuilder, String key, String value) throws IOException {
        try {
            requestBuilder.addHeader(key, value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid value for header '" + key + "'.");
        }
    }

    private static void streamToFile(InputStream in, File destination) throws IOException {
        try (InputStream input = in; OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void verifyOrThrow(File file, String expectedSha256, String redactedUrl) throws IOException {
        if (expectedSha256 == null) {
            return;
        }
        String actual = UrlDigest.sha256Hex(file);
        if (!expectedSha256.equalsIgnoreCase(actual)) {
            throw new IOException("sha256 mismatch downloading " + redactedUrl + ": expected " + expectedSha256 + " but got " + actual);
        }
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
