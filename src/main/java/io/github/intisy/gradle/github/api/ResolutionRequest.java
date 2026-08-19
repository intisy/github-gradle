package io.github.intisy.gradle.github.api;

/**
 * A single coordinate to resolve to a jar, naming either a release version or a source branch
 * and commit. Callers construct one via {@link #fromRelease(String, String, String)} or
 * {@link #fromSource(String, String, String, String)} and pass it to {@link JarResolver#resolve}.
 */
public final class ResolutionRequest {
    enum Strategy {
        RELEASE,
        SOURCE
    }

    private final Strategy strategy;
    private final String owner;
    private final String repo;
    private final String version;
    private final String branch;
    private final String commitSha;

    private ResolutionRequest(Strategy strategy, String owner, String repo, String version, String branch, String commitSha) {
        this.strategy = strategy;
        this.owner = owner;
        this.repo = repo;
        this.version = version;
        this.branch = branch;
        this.commitSha = commitSha;
    }

    /**
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param version the release tag to resolve.
     * @return a request that {@link JarResolver#resolve} satisfies from a published release.
     * @throws IllegalArgumentException if any argument is null.
     */
    public static ResolutionRequest fromRelease(String owner, String repo, String version) {
        requireNonNull(owner, "owner");
        requireNonNull(repo, "repo");
        requireNonNull(version, "version");
        return new ResolutionRequest(Strategy.RELEASE, owner, repo, version, null, null);
    }

    /**
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param branch the branch to clone or pull, or null for the current/default branch.
     * @param commitSha the commit to build, or null to use the branch's latest commit.
     * @return a request that {@link JarResolver#resolve} satisfies by building from source.
     * @throws IllegalArgumentException if {@code owner}, {@code repo}, or {@code branch} is null.
     */
    public static ResolutionRequest fromSource(String owner, String repo, String branch, String commitSha) {
        requireNonNull(owner, "owner");
        requireNonNull(repo, "repo");
        requireNonNull(branch, "branch");
        return new ResolutionRequest(Strategy.SOURCE, owner, repo, null, branch, commitSha);
    }

    private static void requireNonNull(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    /**
     * @return the GitHub account or organization that owns the requested repository.
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @return the requested repository name, without the owner prefix.
     */
    public String getRepo() {
        return repo;
    }

    Strategy getStrategy() {
        return strategy;
    }

    String getVersion() {
        return version;
    }

    String getBranch() {
        return branch;
    }

    String getCommitSha() {
        return commitSha;
    }
}
