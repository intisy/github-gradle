package io.github.intisy.gradle.github.api.capability;

import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.api.ReleaseNotFoundException;
import io.github.intisy.gradle.github.api.model.DeclaredDependency;
import io.github.intisy.gradle.github.api.model.Release;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Resolves and downloads GitHub release artifacts.
 */
public interface Releases {
    /**
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @return the latest release's tag, or null if the repository has no releases.
     * @throws RateLimitException if the GitHub API rate limit has been exceeded.
     */
    String latestVersion(String owner, String repo);

    /**
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param tag the release tag to resolve (a "v" prefix is tried both with and without).
     * @return the release identified by {@code tag}.
     * @throws RuntimeException if no release matches {@code tag} (unchecked; the tag is looked up, not I/O).
     * @throws RateLimitException if the GitHub API rate limit has been exceeded.
     */
    Release releaseByTag(String owner, String repo, String tag);

    /**
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @return the latest release, or null if the repository has no releases.
     * @throws RateLimitException if the GitHub API rate limit has been exceeded.
     */
    Release latestRelease(String owner, String repo);

    /**
     * Downloads the default release jar, matching {@code repo.jar}, {@code repo-version.jar},
     * {@code repo-standalone.jar}, or the first plain {@code .jar} asset in that order.
     *
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param version the release tag to resolve (a "v" prefix is tried both with and without).
     * @return the downloaded (or cached) jar file, or an empty {@code Optional} if the release
     * exists but has no matching jar asset. A release that does not exist at all is a different
     * kind of absence and is never represented this way (see {@code @throws} below): the two are
     * not the same thing, since a missing release is almost always a caller mistake (a typo'd
     * version, a deleted or renamed tag) while a missing asset within a release that does exist is
     * a normal outcome.
     * @throws ReleaseNotFoundException if no release matches {@code version}.
     * @throws RuntimeException thrown unchecked by the underlying client if the download itself
     * fails or another API error occurs.
     * @throws RateLimitException if the GitHub API rate limit has been exceeded.
     */
    Optional<File> downloadJar(String owner, String repo, String version);

    /**
     * Downloads a specific classifier asset, matching {@code repo-classifier.jar} exactly.
     *
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param version the release tag to resolve (a "v" prefix is tried both with and without).
     * @param classifier the artifact classifier identifying the asset (e.g. {@code "api"}).
     * @return the classifier asset's jar, or an empty {@code Optional} if the release exists but
     * has no asset named {@code repo-classifier.jar}. As with the 3-argument overload above, a
     * release that does not exist at all is a different kind of absence and is never represented
     * this way.
     * @throws ReleaseNotFoundException if no release matches {@code version}.
     * @throws RuntimeException thrown unchecked by the underlying client if the download itself
     * fails or another API error occurs.
     * @throws RateLimitException if the GitHub API rate limit has been exceeded.
     */
    Optional<File> downloadJar(String owner, String repo, String version, String classifier);

    /**
     * Downloads every module asset published under the reserved {@code :all} classifier, so a
     * consumer of a multi-module release can pull every module jar without naming each one.
     *
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param version the release tag to resolve (a "v" prefix is tried both with and without).
     * @return the downloaded module jars; never null or empty (a release with no module assets throws).
     * @throws RuntimeException thrown unchecked by the underlying client if no release matches
     * {@code version} or if the release has no module assets.
     * @throws RateLimitException if the GitHub API rate limit has been exceeded.
     */
    List<File> downloadAllModuleJars(String owner, String repo, String version);

    /**
     * Resolves {@code owner:repo:version} and its full transitive closure of GitHub-hosted
     * dependencies declared via each jar's embedded {@code META-INF/github-dependencies.json},
     * including the root jar itself.
     *
     * <p>Cycle detection is local to a single call. A caller resolving many independent
     * coordinates that may share transitive dependencies (for example, one call per declared
     * dependency across several build configurations) must deduplicate the combined results
     * itself if it wants each distinct jar added only once.
     *
     * @param owner the GitHub account or organization that owns the root repository.
     * @param repo the root repository name, without the owner prefix.
     * @param version the release tag to resolve (a "v" prefix is tried both with and without).
     * @return the root jar followed by every transitively resolved jar, each appearing once.
     * @throws RuntimeException thrown unchecked by the underlying client if the root or any
     * transitive dependency fails to resolve: no release matches its version, the release has no
     * matching jar asset, or the download itself fails. Unlike {@link #downloadJar(String, String, String)},
     * this method has no way to represent one transitive dependency's absence without abandoning
     * the whole resolution, so it always throws rather than reporting absence in its return type.
     * @throws RateLimitException if the GitHub API rate limit has been exceeded.
     */
    List<File> resolveWithDependencies(String owner, String repo, String version);

    /**
     * @param jar the jar file to inspect; not modified.
     * @return the dependencies declared by {@code jar}'s embedded
     * {@code META-INF/github-dependencies.json}, or an empty list if the jar has no such entry,
     * or if the entry exists but is unreadable or malformed. The unreadable case logs a warning
     * naming {@code jar} so a corrupt artifact is not silently treated as having no dependencies.
     */
    List<DeclaredDependency> declaredDependencies(File jar);
}
