package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Resolves and downloads GitHub release artifacts.
 */
public interface Releases {
    String latestVersion(String owner, String repo);

    File downloadJar(String owner, String repo, String version) throws IOException;

    File downloadJar(String owner, String repo, String version, String classifier) throws IOException;

    List<File> downloadAllModuleJars(String owner, String repo, String version) throws IOException;

    List<DeclaredDependency> declaredDependencies(File jar);
}
