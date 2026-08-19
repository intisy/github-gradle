package io.github.intisy.gradle.github.api;

import java.io.File;
import java.util.List;

/**
 * Resolves and downloads GitHub release artifacts.
 */
public interface Releases {
    /**
     * @return the latest release's tag, or null if the repository has no releases.
     */
    String latestVersion(String owner, String repo);

    /**
     * @return the release identified by {@code tag}.
     * @throws RuntimeException if no release matches {@code tag} (unchecked; the tag is looked up, not I/O).
     */
    Release releaseByTag(String owner, String repo, String tag);

    /**
     * @return the latest release, or null if the repository has no releases.
     */
    Release latestRelease(String owner, String repo);

    File downloadJar(String owner, String repo, String version);

    /**
     * @return the classifier asset's jar, or null if the release has no asset named
     * {@code repo-classifier.jar}.
     */
    File downloadJar(String owner, String repo, String version, String classifier);

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
     * @return the root jar followed by every transitively resolved jar, each appearing once.
     */
    List<File> resolveWithDependencies(String owner, String repo, String version);

    /**
     * @return the dependencies declared by {@code jar}'s embedded
     * {@code META-INF/github-dependencies.json}, or an empty list if the jar has none. A jar
     * whose metadata entry is present but corrupt is indistinguishable from one with no metadata;
     * both return an empty list.
     */
    List<DeclaredDependency> declaredDependencies(File jar);
}
