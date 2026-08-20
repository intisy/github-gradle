package io.github.intisy.gradle.github.api.capability;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Downloads a jar from an arbitrary HTTP(S) URL, with no repository or git host involved.
 */
public interface Downloads {
    /**
     * @param jarUrl the exact URL to download from.
     * @param headers request headers (for example, an auth token) to send with the download; may
     *                be null or empty.
     * @param sha256 the expected SHA-256 of the downloaded jar, hex-encoded, or null to skip
     *               integrity verification.
     * @return the cached jar for {@code jarUrl}, downloaded only when not already cached.
     * @throws IOException if the download fails, or the downloaded content does not match {@code sha256}.
     */
    File download(String jarUrl, Map<String, String> headers, String sha256) throws IOException;
}
