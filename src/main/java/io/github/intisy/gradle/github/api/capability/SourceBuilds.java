package io.github.intisy.gradle.github.api.capability;

import java.io.File;
import java.io.IOException;

/**
 * Builds a git repository from source and caches the resulting jar by resolved commit.
 */
public interface SourceBuilds {
    /**
     * Builds a GitHub repository from source, identified by owner and repo.
     *
     * @param owner     the repository owner.
     * @param repo      the repository name.
     * @param branch    the branch to clone or pull, or null for the current/default branch.
     * @param commitSha the commit to build, or null to use the branch's latest commit.
     * @return the cached jar for the resolved commit, built only when not already cached.
     * @throws IOException if the clone/pull, checkout, or build itself fails.
     */
    File buildFromSource(String owner, String repo, String branch, String commitSha) throws IOException;

    /**
     * Builds any git repository from source, identified by an explicit clone URL rather than a
     * GitHub owner/repo, so hosts other than github.com are equally supported.
     *
     * @param cloneUrl the exact URL to clone from.
     * @param ref      the branch, tag, or commit to build, or null for the remote's default branch.
     * @return the cached jar for the resolved ref, built only when not already cached.
     * @throws IOException if the clone, checkout, or build itself fails.
     */
    File buildFromGit(String cloneUrl, String ref) throws IOException;
}
