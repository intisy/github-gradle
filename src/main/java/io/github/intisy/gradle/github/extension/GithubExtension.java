package io.github.intisy.gradle.github.extension;

import java.io.File;
import java.nio.file.Path;
import org.gradle.api.Action;
import groovy.lang.Closure;

/**
 * Extension for configuring GitHub integration.
 *
 * <pre>
 * github {
 *     accessToken = "ghp_..."   // or a file/path containing the token
 *     debug = true
 *     skipOnRateLimit = true    // skip (don't fail) when a GitHub rate limit is hit
 *     useCli = true             // route API calls through the local "gh" CLI
 *
 *     publish {
 *         owner   = "my-org"
 *         repo    = "my-repo"
 *         version = "2.0.0"
 *         jar     = file("build/libs/my-fat.jar")
 *     }
 *
 *     resources {
 *         repoUrl = "https://github.com/my-org/my-resources"
 *         branch  = "main"
 *     }
 * }
 * </pre>
 */
@SuppressWarnings("unused")
public class GithubExtension {
    private final ResourcesExtension resources = new ResourcesExtension();
    private final PublishExtension publish = new PublishExtension();

    private String accessToken;
    private boolean debug;
    private boolean skipOnRateLimit;
    private boolean useCli;

    /**
     * @param debug Whether to enable debug logging.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * @return Whether debug logging is enabled.
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Controls how a GitHub API rate limit is handled. When enabled, the plugin degrades gracefully
     * instead of failing the build: a rate-limited dependency resolution falls back to the cached
     * (possibly outdated) jar when one exists, and an update check keeps the currently declared
     * version. Only when no cached copy exists is the dependency skipped. Defaults to {@code false},
     * which aborts the build on a rate limit.
     *
     * @param skipOnRateLimit whether to degrade gracefully (rather than fail) when the rate limit is hit.
     */
    public void setSkipOnRateLimit(boolean skipOnRateLimit) {
        this.skipOnRateLimit = skipOnRateLimit;
    }

    /**
     * @return whether rate-limited operations are skipped instead of failing the build.
     */
    public boolean isSkipOnRateLimit() {
        return skipOnRateLimit;
    }

    /**
     * Controls whether GitHub REST calls are routed through the local {@code gh} CLI instead of
     * direct HTTP. When enabled, the CLI's own authentication and higher rate limits are used;
     * if {@code gh} is not installed the plugin falls back to HTTP. Defaults to {@code false}.
     *
     * @param useCli whether to use the local {@code gh} CLI for API calls.
     */
    public void setUseCli(boolean useCli) {
        this.useCli = useCli;
    }

    /**
     * @return whether API calls are routed through the local {@code gh} CLI.
     */
    public boolean isUseCli() {
        return useCli;
    }

    /**
     * @param accessToken The path to the access token.
     */
    public void setAccessToken(Path accessToken) {
        this.accessToken = accessToken.toString();
    }

    /**
     * @param accessToken The file containing the access token.
     */
    public void setAccessToken(File accessToken) {
        this.accessToken = accessToken.toString();
    }

    /**
     * @param accessToken The access token string.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * @return The access token.
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * @return The nested publish extension.
     */
    public PublishExtension getPublish() {
        return publish;
    }

    /**
     * Configures the nested publish extension using a Gradle action.
     *
     * @param action The configuration action.
     */
    public void publish(Action<? super PublishExtension> action) {
        action.execute(publish);
    }

    /**
     * Configures the nested publish extension using a Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code publish { ... }}
     *
     * @param closure The configuration closure.
     */
    public void publish(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(publish);
        closure.call(publish);
    }

    /**
     * @return The nested resources extension.
     */
    public ResourcesExtension getResources() {
        return resources;
    }

    /**
     * Configures the nested resources extension using a Gradle action.
     *
     * @param action The configuration action.
     */
    public void resources(Action<? super ResourcesExtension> action) {
        action.execute(resources);
    }

    /**
     * Configures the nested resources extension using a Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code resources { ... }}
     *
     * @param closure The configuration closure.
     */
    public void resources(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(resources);
        closure.call(resources);
    }
}
