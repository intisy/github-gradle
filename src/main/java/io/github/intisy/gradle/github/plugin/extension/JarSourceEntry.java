package io.github.intisy.gradle.github.plugin.extension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single {@code sources { jar { } } } entry: a direct jar URL to download and add to a native
 * Gradle configuration, with no repository or git host involved.
 *
 * <pre>
 * sources {
 *     jar {
 *         url = "https://nexus.internal/libs/foo-1.0.jar"
 *         header "Authorization", "Bearer ${myToken}"
 *         sha256 = "..."               // optional integrity check
 *         into = "implementation"      // native configuration; optional, default "implementation"
 *     }
 * }
 * </pre>
 */
@SuppressWarnings("unused")
public class JarSourceEntry {

    private String url;
    private String sha256;
    private String into = "implementation";
    private final Map<String, String> headers = new LinkedHashMap<String, String>();

    /**
     * @param url the exact URL to download a jar from.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @return the exact URL to download a jar from, or null if not set.
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param sha256 the expected SHA-256 of the downloaded jar, hex-encoded, or null to skip
     *               integrity verification.
     */
    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    /**
     * @return the expected SHA-256 of the downloaded jar, hex-encoded, or null if not set.
     */
    public String getSha256() {
        return sha256;
    }

    /**
     * @param into the native Gradle configuration the downloaded jar is added to. Defaults to
     *             {@code "implementation"}.
     */
    public void setInto(String into) {
        this.into = into;
    }

    /**
     * @return the native Gradle configuration the downloaded jar is added to.
     */
    public String getInto() {
        return into;
    }

    /**
     * Adds a single request header (for example, an auth token) to send with the download.
     *
     * @param name the header name.
     * @param value the header value.
     */
    public void header(String name, String value) {
        headers.put(name, value);
    }

    /**
     * @return every header added via {@link #header(String, String)}, in declaration order; never
     * null, empty when none were added.
     */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }
}
