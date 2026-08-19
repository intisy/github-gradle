package io.github.intisy.gradle.github.impl.download;

import io.github.intisy.gradle.github.api.capability.Downloads;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Downloads a jar from an arbitrary HTTP(S) URL, caching it under a directory keyed by a hash of
 * {@code jarUrl}, and verifying an optional expected SHA-256 of the downloaded content.
 *
 * @implNote Header values are user secrets. This class never writes one to a log line, an
 * exception message, or a cache path: log lines and exception messages here only ever name {@code
 * jarUrl} and content hashes, and the cache file name is a hash of {@code jarUrl} alone, so a
 * header value can reach only the outgoing {@link okhttp3.Request} itself.
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

        File cachedJar = new File(cacheDir, sha256Hex(jarUrl.getBytes(StandardCharsets.UTF_8)) + ".jar");
        if (cachedJar.isFile()) {
            logger.debug("Using cached download: " + cachedJar.getName());
            verifyOrThrow(cachedJar, sha256, jarUrl);
            return cachedJar;
        }

        Request.Builder requestBuilder = new Request.Builder().url(jarUrl);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                addHeaderOrThrow(requestBuilder, header.getKey(), header.getValue());
            }
        }
        logger.log("Downloading jar from " + jarUrl);

        File tempFile = File.createTempFile("download-", ".tmp", cacheDir);
        try {
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to download " + jarUrl + ": HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty response body when downloading " + jarUrl + ".");
                }
                streamToFile(body.byteStream(), tempFile);
            }
            verifyOrThrow(tempFile, sha256, jarUrl);
            Files.move(tempFile.toPath(), cachedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            logger.error("Failed to download " + jarUrl + ": " + e.getMessage(), e);
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

    private static void verifyOrThrow(File file, String expectedSha256, String jarUrl) throws IOException {
        if (expectedSha256 == null) {
            return;
        }
        String actual = fileSha256(file);
        if (!expectedSha256.equalsIgnoreCase(actual)) {
            throw new IOException("sha256 mismatch downloading " + jarUrl + ": expected " + expectedSha256 + " but got " + actual);
        }
    }

    private static String fileSha256(File file) throws IOException {
        MessageDigest digest = newSha256Digest();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static String sha256Hex(byte[] value) {
        return toHex(newSha256Digest().digest(value));
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
