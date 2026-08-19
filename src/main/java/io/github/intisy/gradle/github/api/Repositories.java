package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

/**
 * Clones, updates and inspects a local checkout of a GitHub repository.
 *
 * <p>{@link #cloneOrPull} only honours its {@code owner}/{@code repo} arguments for the initial
 * clone. Once a checkout already exists, the update path resolves the owner from the
 * implementation's own {@linkplain #configuredRepo() configured repository} instead, and fails if
 * none is configured; the arguments are then ignored.
 */
public interface Repositories {
    void cloneOrPull(File target, String owner, String repo, String branch) throws IOException;

    boolean exists(File path);

    boolean isUpToDate(File path);

    RemoteRepo remoteOf(File projectDir);

    /**
     * The owner and repository parsed from the implementation's own configured repository URL,
     * the same coordinate {@link #cloneOrPull}'s update path uses.
     *
     * @return the configured owner and repo, or a {@link RemoteRepo} with null fields if none is configured.
     */
    RemoteRepo configuredRepo();
}
