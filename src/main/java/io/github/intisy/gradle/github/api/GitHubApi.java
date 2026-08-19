package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.impl.BuildInvoker;
import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.impl.SourceBuilder;
import io.github.intisy.gradle.github.utils.FileUtils;

import java.io.File;

/**
 * The single entry point for consuming the GitHub client outside a Gradle build.
 *
 * <p>Construct one with {@link #create(GitHubConfig, ResourcesExtension, GitHubLogger)} (or the
 * two-argument overload, which logs to {@code System.err}), then reach each capability through
 * its accessor: {@link #credentials()}, {@link #repositories()}, {@link #releases()},
 * {@link #publishing()}, {@link #sourceBuilds()}.
 */
public final class GitHubApi {
    private final GitHub gitHub;
    private final SourceBuilder sourceBuilder;

    private GitHubApi(GitHub gitHub, GitHubLogger logger) {
        this.gitHub = gitHub;
        File cacheDir = FileUtils.getGradleHome().resolve("github-source").toFile();
        this.sourceBuilder = new SourceBuilder(gitHub, logger, cacheDir, new BuildInvoker.Gradlew(logger));
    }

    /**
     * @param config    the access token and auth/cli/resilience settings.
     * @param resources the configured resource repository, if any.
     * @param logger    receives diagnostic output.
     */
    public static GitHubApi create(GitHubConfig config, ResourcesExtension resources, GitHubLogger logger) {
        return new GitHubApi(new GitHub(logger, resources, config), logger);
    }

    /**
     * Same as {@link #create(GitHubConfig, ResourcesExtension, GitHubLogger)}, logging to
     * {@code System.err} via {@link ConsoleGitHubLogger}.
     */
    public static GitHubApi create(GitHubConfig config, ResourcesExtension resources) {
        return create(config, resources, new ConsoleGitHubLogger(false));
    }

    public Credentials credentials() {
        return gitHub;
    }

    public Repositories repositories() {
        return gitHub;
    }

    public Releases releases() {
        return gitHub;
    }

    public Publishing publishing() {
        return gitHub;
    }

    public SourceBuilds sourceBuilds() {
        return sourceBuilder;
    }
}
