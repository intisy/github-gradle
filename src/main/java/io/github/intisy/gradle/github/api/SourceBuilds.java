package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

/**
 * Builds a GitHub repository from source and caches the resulting jar by commit.
 */
public interface SourceBuilds {
    /**
     * @param owner     the repository owner.
     * @param repo      the repository name.
     * @param branch    the branch to clone or pull, or null for the current/default branch.
     * @param commitSha the commit to build, or null to use the branch's latest commit.
     * @return the cached jar for the resolved commit, built only when not already cached.
     */
    File buildFromSource(String owner, String repo, String branch, String commitSha) throws IOException;
}
