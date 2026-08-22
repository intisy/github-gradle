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
 *         dir = "java"                 // gradle project directory; optional, default the checkout root
 *         modules = "routing contracts" // modules whose jars to take; optional, default the root jar
 *         into = "implementation"      // native configuration; optional, default "implementation"
 *     }
 * }
 * </pre>
 */
@SuppressWarnings("unused")
public class GitSourceEntry {

    private String url;
    private String ref;
    private String dir;
    private String modules;
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
     * @param dir the gradle project directory relative to the checkout root, or null when the build
     *            lives at the root. A repository whose gradle root is a subdirectory is otherwise
     *            unbuildable, because the wrapper is not where the checkout is.
     */
    public void setDir(String dir) {
        this.dir = dir;
    }

    /**
     * @return the gradle project directory relative to the checkout root, or null if not set.
     */
    public String getDir() {
        return dir;
    }

    /**
     * @param modules whitespace-separated gradle module names whose jars to take, or null to take
     *                the single jar the root project produces.
     */
    public void setModules(String modules) {
        this.modules = modules;
    }

    /**
     * @return the declared module names, in declaration order; empty when none were named.
     */
    public java.util.List<String> getModules() {
        java.util.List<String> named = new java.util.ArrayList<String>();
        if (modules != null) {
            for (String module : modules.trim().split("\\s+")) {
                if (!module.isEmpty()) {
                    named.add(module);
                }
            }
        }
        return named;
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
