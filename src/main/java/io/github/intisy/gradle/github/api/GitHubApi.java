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
     * @return a new client wired to the given configuration and logger.
     */
    public static GitHubApi create(GitHubConfig config, ResourcesExtension resources, GitHubLogger logger) {
        return new GitHubApi(new GitHub(logger, resources, config), config, logger);
    }

    /**
     * Same as {@link #create(GitHubConfig, ResourcesExtension, GitHubLogger)}, logging to
     * {@code System.err} via {@link ConsoleGitHubLogger}.
     *
     * @param config    the access token and auth/cli/resilience settings.
     * @param resources the configured resource repository, if any.
     * @return a new client wired to the given configuration, logging to {@code System.err}.
     */
    public static GitHubApi create(GitHubConfig config, ResourcesExtension resources) {
        return create(config, resources, new ConsoleGitHubLogger(false));
    }

    /**
     * @return the resolved API token and SSH key for this client's configuration.
     */
    public Credentials credentials() {
        return gitHub;
    }

    /**
     * @return the client's checkout-cloning, pulling and inspection capability.
     */
    public Repositories repositories() {
        return repositories;
    }

    /**
     * @return the client's release lookup and jar download capability.
     */
    public Releases releases() {
        return gitHub;
    }

    /**
     * @return the client's release creation and asset upload capability.
     */
    public Publishing publishing() {
        return gitHub;
    }

    /**
     * @return the client's build-from-source capability, backed by a shared cache directory whose
     * entries are keyed by owner, repo, and resolved commit.
     */
    public SourceBuilds sourceBuilds() {
        return sourceBuilder;
    }

    /**
     * @return the client's {@link ResolutionRequest}-based resolver, dispatching to {@link #releases()}
     * or {@link #sourceBuilds()} by the request's own strategy.
     */
    public JarResolver resolver() {
        return resolver;
    }
}
