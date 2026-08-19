package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.impl.BuildInvoker;
import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.impl.RepositoriesImpl;
import io.github.intisy.gradle.github.impl.SourceBuilder;
import io.github.intisy.gradle.github.utils.FileUtils;

import java.io.File;

/**
 * The single entry point for consuming the GitHub client outside a Gradle build.
 *
 * <p>Construct one with {@link #create(GitHubConfig, ResourcesExtension, GitHubLogger)} (or the
 * two-argument overload, which logs to {@code System.err}), then reach each capability through
 * its accessor: {@link #credentials()}, {@link #repositories()}, {@link #releases()},
 * {@link #publishing()}, {@link #sourceBuilds()}, {@link #resolver()}.
 */
public final class GitHubApi {
    private final GitHub gitHub;
    private final Repositories repositories;
    private final SourceBuilder sourceBuilder;
    private final JarResolver resolver;

    private GitHubApi(GitHub gitHub, GitHubConfig config, GitHubLogger logger) {
        this.gitHub = gitHub;
        this.repositories = new RepositoriesImpl(gitHub, config, logger);
        File cacheDir = FileUtils.getGradleHome().resolve("github-source").toFile();
        this.sourceBuilder = new SourceBuilder(config, logger, cacheDir, new BuildInvoker.Gradlew(logger));
        this.resolver = new JarResolverImpl(gitHub, sourceBuilder);
    }

    /**
     * @param config    the access token and auth/cli/resilience settings.
     * @param resources the configured resource repository, if any.
     * @param logger    receives diagnostic output.
     */
    public static GitHubApi create(GitHubConfig config, ResourcesExtension resources, GitHubLogger logger) {
        return new GitHubApi(new GitHub(logger, resources, config), config, logger);
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
        return repositories;
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

    public JarResolver resolver() {
        return resolver;
    }
}
