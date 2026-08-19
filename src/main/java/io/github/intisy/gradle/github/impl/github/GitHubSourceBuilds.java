package io.github.intisy.gradle.github.impl.github;

import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.impl.source.SourceBuilder;

import java.io.File;
import java.io.IOException;

/**
 * Adapts the host-agnostic {@link SourceBuilder} to the GitHub-specific {@link SourceBuilds}
 * capability by resolving {@code owner}/{@code repo} to a github.com clone URL before
 * delegating, so the source-build strategy behaves exactly as it did before {@link
 * SourceBuilder} took an explicit clone URL.
 */
public final class GitHubSourceBuilds implements SourceBuilds {
    private final GitHub gitHub;
    private final SourceBuilder sourceBuilder;

    /**
     * @param gitHub resolves the github.com clone URL (SSH or HTTPS) for a given owner/repo.
     * @param sourceBuilder builds and caches the jar once the clone URL is known.
     */
    public GitHubSourceBuilds(GitHub gitHub, SourceBuilder sourceBuilder) {
        this.gitHub = gitHub;
        this.sourceBuilder = sourceBuilder;
    }

    @Override
    public File buildFromSource(String owner, String repo, String branch, String commitSha) throws IOException {
        String cloneUrl = gitHub.getRepositoryURL(owner, repo);
        return sourceBuilder.buildFromSource(cloneUrl, owner, repo, branch, commitSha);
    }
}
