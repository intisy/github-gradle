package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.GitHubConfig;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.api.model.RemoteRepo;
import io.github.intisy.gradle.github.api.capability.Repositories;
import io.github.intisy.gradle.github.extension.ResourcesExtension;

import java.io.File;
import java.io.IOException;

/**
 * {@link Repositories} that builds a fresh, per-call {@link GitHub} scoped to the exact owner and
 * repo an operation names, instead of reusing the single {@link GitHub} {@code GitHubApi.create}
 * was given.
 *
 * @implNote Mirrors {@link SourceBuilder#buildFromSource}: a {@link GitHub} carries one {@link
 * ResourcesExtension}, so its own update path (which resolves the owner from that extension) is
 * only correct for repeated calls against the same repository. {@link #cloneOrPull} builds its own
 * scoped {@link GitHub} per call so a consumer can target any repository, any number of times,
 * without a previous call's target leaking into the next one. {@link #isUpToDate} takes no
 * owner/repo, so it derives the repository identity from the checkout at {@code path} itself (the
 * same way {@link #remoteOf} does) rather than from whichever repository happened to be configured
 * at construction time. {@link #exists}, {@link #remoteOf} and {@link #configuredRepo} genuinely
 * have no repository-specific behavior to scope, so they keep using the construction-time {@link
 * GitHub}.
 */
public class RepositoriesImpl implements Repositories {
    private final GitHub configured;
    private final GitHubConfig config;
    private final GitHubLogger logger;

    /**
     * @param configured the construction-time {@link GitHub}, used as-is by operations that have
     * no repository-specific behavior to scope ({@link #exists}, {@link #remoteOf}, {@link #configuredRepo}).
     * @param config the access token and auth/cli/resilience settings, reused to build each per-call scoped {@link GitHub}.
     * @param logger receives diagnostic output from each per-call scoped {@link GitHub}.
     */
    public RepositoriesImpl(GitHub configured, GitHubConfig config, GitHubLogger logger) {
        this.configured = configured;
        this.config = config;
        this.logger = logger;
    }

    private GitHub scopedTo(String owner, String repo) {
        ResourcesExtension resources = new ResourcesExtension();
        resources.setRepoUrl("https://github.com/" + owner + "/" + repo);
        return new GitHub(logger, resources, config);
    }

    @Override
    public void cloneOrPull(File target, String owner, String repo, String branch) throws IOException {
        scopedTo(owner, repo).cloneOrPull(target, owner, repo, branch);
    }

    @Override
    public boolean exists(File path) {
        return configured.exists(path);
    }

    @Override
    public boolean isUpToDate(File path) {
        RemoteRepo remote = configured.remoteOf(path);
        return scopedTo(remote.getOwner(), remote.getRepo()).isUpToDate(path);
    }

    @Override
    public RemoteRepo remoteOf(File projectDir) {
        return configured.remoteOf(projectDir);
    }

    @Override
    public RemoteRepo configuredRepo() {
        return configured.configuredRepo();
    }
}
