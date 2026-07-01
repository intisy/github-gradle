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
 *     debug = true
 *
 *     auth {
 *         token     = "ghp_..."                 // a Personal Access Token
 *         tokenFile = file("secrets/github.txt") // or a file that contains one
 *         sshKey    = file("~/.ssh/id_ed25519")  // SSH private key for git clone/pull
 *     }
 *
 *     cli {
 *         enabled  = true       // route API calls through the local "gh" CLI
 *         fallback = true       // fall back to HTTP if gh is unavailable or fails (default)
 *     }
 *
 *     resilience {
 *         skipOnRateLimit = true // degrade gracefully (don't fail) when a GitHub rate limit is hit
 *     }
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
    private final CliExtension cli = new CliExtension();
    private final AuthExtension auth = new AuthExtension();
    private final ResilienceExtension resilience = new ResilienceExtension();

    private String accessToken;
    private boolean debug;

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
     * @return the nested auth extension.
     */
    public AuthExtension getAuth() {
        return auth;
    }

    /**
     * Configures the nested auth extension using a Gradle action.
     *
     * @param action The configuration action.
     */
    public void auth(Action<? super AuthExtension> action) {
        action.execute(auth);
    }

    /**
     * Configures the nested auth extension using a Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code auth { ... }}
     *
     * @param closure The configuration closure.
     */
    public void auth(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(auth);
        closure.call(auth);
    }

    /**
     * @return the nested resilience extension.
     */
    public ResilienceExtension getResilience() {
        return resilience;
    }

    /**
     * Configures the nested resilience extension using a Gradle action.
     *
     * @param action The configuration action.
     */
    public void resilience(Action<? super ResilienceExtension> action) {
        action.execute(resilience);
    }

    /**
     * Configures the nested resilience extension using a Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code resilience { ... }}
     *
     * @param closure The configuration closure.
     */
    public void resilience(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(resilience);
        closure.call(resilience);
    }

    /**
     * @param skipOnRateLimit whether to degrade gracefully (rather than fail) when the rate limit is hit.
     * @deprecated Replaced by the nested {@code resilience { skipOnRateLimit = ... }} block. This
     *             delegates to {@link ResilienceExtension#setSkipOnRateLimit(boolean)} and will be
     *             removed in a future release.
     */
    @Deprecated
    public void setSkipOnRateLimit(boolean skipOnRateLimit) {
        resilience.setSkipOnRateLimit(skipOnRateLimit);
    }

    /**
     * @return whether rate-limited operations degrade gracefully instead of failing the build.
     * @deprecated Replaced by the nested {@code resilience { skipOnRateLimit = ... }} block. This
     *             delegates to {@link ResilienceExtension#isSkipOnRateLimit()} and will be removed
     *             in a future release.
     */
    @Deprecated
    public boolean isSkipOnRateLimit() {
        return resilience.isSkipOnRateLimit();
    }

    /**
     * @return the nested CLI extension.
     */
    public CliExtension getCli() {
        return cli;
    }

    /**
     * Configures the nested CLI extension using a Gradle action.
     *
     * @param action The configuration action.
     */
    public void cli(Action<? super CliExtension> action) {
        action.execute(cli);
    }

    /**
     * Configures the nested CLI extension using a Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code cli { ... }}
     *
     * @param closure The configuration closure.
     */
    public void cli(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(cli);
        closure.call(cli);
    }

    /**
     * @param useCli whether to use the local {@code gh} CLI for API calls.
     * @deprecated Replaced by the nested {@code cli { enabled = ... }} block. This delegates to
     *             {@link CliExtension#setEnabled(boolean)} and will be removed in a future release.
     */
    @Deprecated
    public void setUseCli(boolean useCli) {
        cli.setEnabled(useCli);
    }

    /**
     * @return whether API calls are routed through the local {@code gh} CLI.
     * @deprecated Replaced by the nested {@code cli { enabled = ... }} block. This delegates to
     *             {@link CliExtension#isEnabled()} and will be removed in a future release.
     */
    @Deprecated
    public boolean isUseCli() {
        return cli.isEnabled();
    }

    /**
     * @param accessToken The path to the access token.
     * @deprecated Replaced by the nested {@code auth { }} block ({@code tokenFile} / {@code sshKey}).
     *             Still honoured as a fallback and will be removed in a future release.
     */
    @Deprecated
    public void setAccessToken(Path accessToken) {
        this.accessToken = accessToken.toString();
    }

    /**
     * @param accessToken The file containing the access token.
     * @deprecated Replaced by the nested {@code auth { }} block ({@code tokenFile} / {@code sshKey}).
     *             Still honoured as a fallback and will be removed in a future release.
     */
    @Deprecated
    public void setAccessToken(File accessToken) {
        this.accessToken = accessToken.toString();
    }

    /**
     * @param accessToken The access token string.
     * @deprecated Replaced by the nested {@code auth { token = ... }} block. Still honoured as a
     *             fallback and will be removed in a future release.
     */
    @Deprecated
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * @return The access token configured via the deprecated {@code accessToken} field, or null.
     * @deprecated Prefer {@link #getAuth()}. Retained so the plugin can honour the legacy field.
     */
    @Deprecated
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
