package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.api.capability.Credentials;
import io.github.intisy.gradle.github.api.capability.Downloads;
import io.github.intisy.gradle.github.api.capability.JarResolver;
import io.github.intisy.gradle.github.api.capability.Publishing;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.api.capability.Repositories;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.api.config.GitHubConfig;
import io.github.intisy.gradle.github.api.log.ConsoleGitHubLogger;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.api.model.ResolutionRequest;
import io.github.intisy.gradle.github.api.config.ResourceSettings;
import io.github.intisy.gradle.github.impl.JarResolverImpl;
import io.github.intisy.gradle.github.impl.RepositoriesImpl;
import io.github.intisy.gradle.github.impl.download.UrlDownloads;
import io.github.intisy.gradle.github.impl.github.GitHub;
import io.github.intisy.gradle.github.impl.github.GitHubSourceBuilds;
import io.github.intisy.gradle.github.impl.source.BuildInvoker;
import io.github.intisy.gradle.github.impl.source.SourceBuilder;
import io.github.intisy.gradle.github.utils.FileUtils;
import okhttp3.OkHttpClient;

import java.io.File;

/**
 * The single entry point for consuming the GitHub client outside a Gradle build.
 *
 * <p>Construct one with {@link #create(GitHubConfig, ResourceSettings, GitHubLogger)} (or the
 * two-argument overload, which logs to {@code System.err}, or the no-argument overload, which
 * additionally defaults to fully anonymous access), then reach each capability through its
 * accessor: {@link #credentials()}, {@link #repositories()}, {@link #releases()},
 * {@link #publishing()}, {@link #sourceBuilds()}, {@link #downloads()}, {@link #resolver()}.
 */
public final class GitHubApi {
    private final GitHub gitHub;
    private final Repositories repositories;
    private final SourceBuilds sourceBuilds;
    private final Downloads downloads;
    private final JarResolver resolver;

    private GitHubApi(GitHub gitHub, GitHubConfig config, GitHubLogger logger) {
        this.gitHub = gitHub;
        this.repositories = new RepositoriesImpl(gitHub, config, logger);
        File sourceCacheDir = FileUtils.getGradleHome().resolve("github-source").toFile();
        SourceBuilder sourceBuilder = new SourceBuilder(config, logger, sourceCacheDir, new BuildInvoker.Gradlew(logger));
        this.sourceBuilds = new GitHubSourceBuilds(gitHub, sourceBuilder);
        File downloadCacheDir = FileUtils.getGradleHome().resolve("url-downloads").toFile();
        this.downloads = new UrlDownloads(new OkHttpClient(), logger, downloadCacheDir);
        this.resolver = new JarResolverImpl(gitHub, sourceBuilds, downloads);
    }

    /**
     * @param config    the access token and auth/cli/resilience settings.
     * @param resources the configured resource repository, if any.
     * @param logger    receives diagnostic output.
     * @return a new client wired to the given configuration and logger.
     */
    public static GitHubApi create(GitHubConfig config, ResourceSettings resources, GitHubLogger logger) {
        return new GitHubApi(new GitHub(logger, resources, config), config, logger);
    }

    /**
     * Same as {@link #create(GitHubConfig, ResourceSettings, GitHubLogger)}, logging to
     * {@code System.err} via {@link ConsoleGitHubLogger}.
     *
     * @param config    the access token and auth/cli/resilience settings.
     * @param resources the configured resource repository, if any.
     * @return a new client wired to the given configuration, logging to {@code System.err}.
     */
    public static GitHubApi create(GitHubConfig config, ResourceSettings resources) {
        return create(config, resources, new ConsoleGitHubLogger(false));
    }

    /**
     * Same as {@link #create(GitHubConfig, ResourceSettings, GitHubLogger)}, defaulting to an
     * anonymous {@link GitHubConfig}, a default {@link ResourceSettings}, and logging to
     * {@code System.err} via {@link ConsoleGitHubLogger}.
     *
     * @return a new client for fully anonymous, unauthenticated access.
     */
    public static GitHubApi create() {
        return create(GitHubConfig.builder().build(), new ResourceSettings(), new ConsoleGitHubLogger(false));
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
     * entries are keyed by owner, repo, and resolved commit (or, for an arbitrary clone URL, a
     * derived identity and resolved ref).
     */
    public SourceBuilds sourceBuilds() {
        return sourceBuilds;
    }

    /**
     * @return the client's jar-download capability, for a URL with no repository or git host
     * involved, backed by a shared cache directory whose entries are keyed by a hash of the URL.
     */
    public Downloads downloads() {
        return downloads;
    }

    /**
     * @return the client's {@link ResolutionRequest}-based resolver, dispatching to {@link
     * #releases()}, {@link #sourceBuilds()}, or {@link #downloads()} by the request's own strategy.
     */
    public JarResolver resolver() {
        return resolver;
    }
}
