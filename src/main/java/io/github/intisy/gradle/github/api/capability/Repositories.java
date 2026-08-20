package io.github.intisy.gradle.github.api.capability;

import io.github.intisy.gradle.github.api.model.RemoteRepo;

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
    /**
     * Clones {@code owner/repo} into {@code target} if no checkout exists there yet, otherwise
     * pulls the latest changes for {@code branch} (or the current branch, if null).
     *
     * @param target the local directory to clone into or pull within.
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param branch the branch to clone or pull, or null for the current/default branch.
     * @throws IOException if the clone or pull fails.
     */
    void cloneOrPull(File target, String owner, String repo, String branch) throws IOException;

    /**
     * Clones or pulls {@code cloneUrl} into {@code target}, using the given URL directly rather
     * than reconstructing one from an owner and repo, so any git host is honoured exactly as
     * configured (github.com, GitHub Enterprise, or any other host).
     *
     * @param target the local directory to clone into or pull within.
     * @param cloneUrl the exact URL to clone from.
     * @param branch the branch to clone or pull, or null for the current/default branch.
     * @throws IOException if the clone or pull fails.
     */
    void cloneOrPullFrom(File target, String cloneUrl, String branch) throws IOException;

    /**
     * @param path the directory to check.
     * @return true if a git repository checkout exists at {@code path}.
     */
    boolean exists(File path);

    /**
     * @param path the checkout to check, whose own {@code origin} remote identifies the repository.
     * @return true if {@code path}'s current branch matches its remote counterpart, false if it
     * is behind or the check itself fails (e.g. no network access).
     */
    boolean isUpToDate(File path);

    /**
     * @param projectDir the checkout whose {@code origin} remote is parsed.
     * @return the owner and repository name parsed from {@code projectDir}'s {@code origin} remote URL.
     * @throws RuntimeException if {@code projectDir} has no {@code origin} remote configured, or its
     * URL cannot be parsed into an owner and repository name.
     */
    RemoteRepo remoteOf(File projectDir);

    /**
     * The owner and repository parsed from the implementation's own configured repository URL.
     *
     * @return the configured owner and repo, or a {@link RemoteRepo} with null fields if none is configured.
     */
    RemoteRepo configuredRepo();
}
