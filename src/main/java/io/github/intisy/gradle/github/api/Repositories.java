package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

/**
 * Clones, updates and inspects a local checkout of a GitHub repository.
 */
public interface Repositories {
    void cloneOrPull(File target, String owner, String repo, String branch) throws IOException;

    boolean exists(File path);

    boolean isUpToDate(File path);

    RemoteRepo remoteOf(File projectDir);
}
