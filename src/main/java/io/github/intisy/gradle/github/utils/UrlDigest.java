package io.github.intisy.gradle.github.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hashing shared by every cache that is keyed by a hash of a URL rather than the URL
 * itself, so a credential embedded in the URL never has to be written to a cache path.
 */
public final class UrlDigest {
    private UrlDigest() {
    }

    /**
     * @param value the string to hash, encoded as UTF-8.
     * @return the hex-encoded SHA-256 of {@code value}.
     */
    public static String sha256Hex(String value) {
        return toHex(newSha256Digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * @param file the file whose content to hash.
     * @return the hex-encoded SHA-256 of {@code file}'s content.
     * @throws IOException if {@code file} cannot be read.
     */
    public static String sha256Hex(File file) throws IOException {
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
}
