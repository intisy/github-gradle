package io.github.intisy.gradle.github.api.model;

import io.github.intisy.gradle.github.api.capability.JarResolver;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single coordinate to resolve to a jar, naming a release version, a GitHub source branch and
 * commit, an arbitrary git clone URL and ref, or a direct jar URL. Callers construct one via
 * {@link #fromRelease(String, String, String)}, {@link #fromSource(String, String, String, String)},
 * {@link #fromGit(String, String)}, or {@link #fromUrl(String, Map, String)} and pass it to
 * {@link JarResolver#resolve}.
 */
public final class ResolutionRequest {
    /**
     * Which capability a {@link ResolutionRequest} is satisfied by.
     */
    public enum Strategy {
        /** Satisfied by {@link Releases#downloadJar(String, String, String)}. */
        RELEASE,
        /** Satisfied by {@link SourceBuilds#buildFromSource(String, String, String, String)}. */
        SOURCE,
        /** Satisfied by {@link SourceBuilds#buildFromSource(String, String)}. */
        GIT,
        /** Satisfied by a {@code Downloads} capability, given {@link ResolutionRequest#getJarUrl()} and {@link ResolutionRequest#getHeaders()}. */
        URL
    }

    private final Strategy strategy;
    private final String owner;
    private final String repo;
    private final String version;
    private final String branch;
    private final String commitSha;
    private final String cloneUrl;
    private final String ref;
    private final String jarUrl;
    private final Map<String, String> headers;
    private final String sha256;

    private ResolutionRequest(Strategy strategy, String owner, String repo, String version, String branch,
            String commitSha, String cloneUrl, String ref, String jarUrl, Map<String, String> headers, String sha256) {
        this.strategy = strategy;
        this.owner = owner;
        this.repo = repo;
        this.version = version;
        this.branch = branch;
        this.commitSha = commitSha;
        this.cloneUrl = cloneUrl;
        this.ref = ref;
        this.jarUrl = jarUrl;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
        this.sha256 = sha256;
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
        return new ResolutionRequest(Strategy.RELEASE, owner, repo, version, null, null, null, null, null, null, null);
    }

    /**
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param branch the branch to clone or pull.
     * @param commitSha the commit to build, or null to use the branch's latest commit.
     * @return a request that {@link JarResolver#resolve} satisfies by building from source.
     * @throws IllegalArgumentException if {@code owner}, {@code repo}, or {@code branch} is null.
     */
    public static ResolutionRequest fromSource(String owner, String repo, String branch, String commitSha) {
        requireNonNull(owner, "owner");
        requireNonNull(repo, "repo");
        requireNonNull(branch, "branch");
        return new ResolutionRequest(Strategy.SOURCE, owner, repo, null, branch, commitSha, null, null, null, null, null);
    }

    /**
     * @param cloneUrl the exact URL to clone from; any git host, not just github.com.
     * @param ref the branch, tag, or commit to build, or null for the remote's default branch.
     * @return a request that {@link JarResolver#resolve} satisfies by cloning and building {@code cloneUrl}.
     * @throws IllegalArgumentException if {@code cloneUrl} is null.
     */
    public static ResolutionRequest fromGit(String cloneUrl, String ref) {
        requireNonNull(cloneUrl, "cloneUrl");
        return new ResolutionRequest(Strategy.GIT, null, null, null, null, null, cloneUrl, ref, null, null, null);
    }

    /**
     * Same as {@link #fromUrl(String, Map, String)} with no expected sha256.
     *
     * @param jarUrl the exact URL to download a jar from.
     * @param headers request headers (for example, an auth token) to send with the download; may
     *                be null or empty.
     * @return a request that {@link JarResolver#resolve} satisfies by downloading {@code jarUrl}.
     * @throws IllegalArgumentException if {@code jarUrl} is null.
     */
    public static ResolutionRequest fromUrl(String jarUrl, Map<String, String> headers) {
        return fromUrl(jarUrl, headers, null);
    }

    /**
     * @param jarUrl the exact URL to download a jar from.
     * @param headers request headers (for example, an auth token) to send with the download; may
     *                be null or empty.
     * @param sha256 the expected SHA-256 of the downloaded jar, hex-encoded, or null to skip
     *               integrity verification.
     * @return a request that {@link JarResolver#resolve} satisfies by downloading {@code jarUrl}.
     * @throws IllegalArgumentException if {@code jarUrl} is null.
     */
    public static ResolutionRequest fromUrl(String jarUrl, Map<String, String> headers, String sha256) {
        requireNonNull(jarUrl, "jarUrl");
        Map<String, String> safeHeaders = headers == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
        return new ResolutionRequest(Strategy.URL, null, null, null, null, null, null, null, jarUrl, safeHeaders, sha256);
    }

    private static void requireNonNull(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    /**
     * @return the GitHub account or organization that owns the requested repository, or null for
     * a {@link Strategy#GIT} or {@link Strategy#URL} request.
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @return the requested repository name, without the owner prefix, or null for a
     * {@link Strategy#GIT} or {@link Strategy#URL} request.
     */
    public String getRepo() {
        return repo;
    }

    /**
     * @return which capability this request is satisfied by.
     */
    public Strategy getStrategy() {
        return strategy;
    }

    /**
     * @return the release tag to resolve, or null unless this is a {@link Strategy#RELEASE} request.
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return the branch to clone or pull, or null unless this is a {@link Strategy#SOURCE} request.
     */
    public String getBranch() {
        return branch;
    }

    /**
     * @return the commit to build, or null unless this is a {@link Strategy#SOURCE} request naming one.
     */
    public String getCommitSha() {
        return commitSha;
    }

    /**
     * @return the exact URL to clone from, or null unless this is a {@link Strategy#GIT} request.
     */
    public String getCloneUrl() {
        return cloneUrl;
    }

    /**
     * @return the branch, tag, or commit to build, or null unless this is a {@link Strategy#GIT} request.
     */
    public String getRef() {
        return ref;
    }

    /**
     * @return the exact URL to download a jar from, or null unless this is a {@link Strategy#URL} request.
     */
    public String getJarUrl() {
        return jarUrl;
    }

    /**
     * @return the request headers to send with a {@link Strategy#URL} download; never null, empty
     * when none were given or this is not a {@link Strategy#URL} request.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * @return the expected SHA-256 of the downloaded jar, hex-encoded, or null if none was given
     * or this is not a {@link Strategy#URL} request.
     */
    public String getSha256() {
        return sha256;
    }
}
