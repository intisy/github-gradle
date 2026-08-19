package io.github.intisy.gradle.github.plugin.extension;

/**
 * A single {@code sources { git { } } } entry: an arbitrary git repository to clone, build, and
 * add to a native Gradle configuration.
 *
 * <pre>
 * sources {
 *     git {
 *         url = "https://gitlab.com/me/lib.git"
 *         ref = "main"                 // branch, tag or commit; optional, default the remote's default branch
 *         into = "implementation"      // native configuration; optional, default "implementation"
 *     }
 * }
 * </pre>
 */
@SuppressWarnings("unused")
public class GitSourceEntry {

    private String url;
    private String ref;
    private String into = "implementation";

    /**
     * @param url the exact URL to clone from; any git host, not just github.com.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @return the exact URL to clone from, or null if not set.
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param ref the branch, tag, or commit to build, or null for the remote's default branch.
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    /**
     * @return the branch, tag, or commit to build, or null for the remote's default branch.
     */
    public String getRef() {
        return ref;
    }

    /**
     * @param into the native Gradle configuration the built jar is added to. Defaults to
     *             {@code "implementation"}.
     */
    public void setInto(String into) {
        this.into = into;
    }

    /**
     * @return the native Gradle configuration the built jar is added to.
     */
    public String getInto() {
        return into;
    }
}
