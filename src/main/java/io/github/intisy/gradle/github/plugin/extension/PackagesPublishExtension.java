package io.github.intisy.gradle.github.plugin.extension;

/**
 * Configures whether {@code publishGithub} also deploys the project's Maven publications to GitHub
 * Packages, alongside the jars it attaches to the GitHub release.
 *
 * <pre>
 * publishGithub {
 *     packages {
 *         enabled = true
 *     }
 * }
 * </pre>
 *
 * <p>{@code owner} and {@code repo} fall back to the ones {@code publishGithub} already resolves, so
 * an adopting build states nothing but {@code enabled}.
 *
 * @apiNote The two destinations are not alternatives. GitHub Packages requires a token for every
 * read, including a read of a public package, while a release asset on a public repository resolves
 * anonymously; and a release asset is a bare jar, while a package carries the pom and Gradle module
 * metadata that transitive dependencies and variants such as test fixtures resolve through. Neither
 * one covers the other's case, which is why this sits beside the release upload rather than
 * replacing it.
 */
@SuppressWarnings("unused")
public class PackagesPublishExtension {

    private boolean enabled;
    private String owner;
    private String repo;

    /**
     * Controls whether {@code publishGithub} also publishes to GitHub Packages. Defaults to
     * {@code false}.
     *
     * @param enabled whether to publish to GitHub Packages.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return whether {@code publishGithub} also publishes to GitHub Packages.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Overrides the owner of the repository the packages are published under.
     *
     * @param owner the repository owner; null takes the one {@code publishGithub} resolves.
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * @return the overridden owner, or null to take the one {@code publishGithub} resolves.
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Overrides the repository the packages are published under.
     *
     * @param repo the repository name; null takes the one {@code publishGithub} resolves.
     */
    public void setRepo(String repo) {
        this.repo = repo;
    }

    /**
     * @return the overridden repository, or null to take the one {@code publishGithub} resolves.
     */
    public String getRepo() {
        return repo;
    }
}
