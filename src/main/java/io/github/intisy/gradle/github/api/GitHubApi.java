package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.impl.GitHub;

/**
 * The single entry point for consuming the GitHub client outside a Gradle build.
 *
 * <p>Construct one with {@link #create(GitHubConfig, ResourcesExtension, GitHubLogger)} (or the
 * two-argument overload, which logs to {@code System.err}), then reach each capability through
 * its accessor: {@link #credentials()}, {@link #repositories()}, {@link #releases()},
 * {@link #publishing()}.
 */
public final class GitHubApi {
    private final GitHub gitHub;

    private GitHubApi(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    /**
     * @param config    the access token and auth/cli/resilience settings.
     * @param resources the configured resource repository, if any.
     * @param logger    receives diagnostic output.
     */
    public static GitHubApi create(GitHubConfig config, ResourcesExtension resources, GitHubLogger logger) {
        return new GitHubApi(new GitHub(logger, resources, config));
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
}
