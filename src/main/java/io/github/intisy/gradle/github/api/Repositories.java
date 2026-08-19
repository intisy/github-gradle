package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

/**
 * Clones, updates and inspects a local checkout of a GitHub repository.
 *
 * <p>{@link #cloneOrPull} honours its {@code owner}/{@code repo} arguments on every call,
 * including the update path for a checkout that already exists, so calling it repeatedly for the
 * same repository, or for different repositories from the same instance, is safe.
 *
 * <p>{@link #isUpToDate} takes no owner/repo argument; it derives the repository identity from the
 * checkout at {@code path} itself, the same way {@link #remoteOf} does, rather than from whatever
 * repository was configured when this instance was created.
 */
public interface Repositories {
    void cloneOrPull(File target, String owner, String repo, String branch) throws IOException;

    boolean exists(File path);

    boolean isUpToDate(File path);

    RemoteRepo remoteOf(File projectDir);

    /**
     * The owner and repository parsed from the implementation's own configured repository URL.
     *
     * @return the configured owner and repo, or a {@link RemoteRepo} with null fields if none is configured.
     */
    RemoteRepo configuredRepo();
}
