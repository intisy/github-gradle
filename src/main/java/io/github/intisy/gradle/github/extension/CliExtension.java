package io.github.intisy.gradle.github.extension;

/**
 * Extension for configuring the local GitHub CLI ({@code gh}) transport.
 *
 * <pre>
 * github {
 *     cli {
 *         enabled  = true   // route GitHub REST calls through the "gh" CLI
 *         fallback = true   // fall back to HTTP when gh is unavailable or a call fails (default)
 *     }
 * }
 * </pre>
 *
 * <p>When {@link #isEnabled() enabled}, API GET/POST requests are executed through {@code gh api},
 * reusing the CLI's own authentication and higher (authenticated) rate limits. When
 * {@link #isFallback() fallback} is left at its default of {@code true}, the plugin transparently
 * reverts to direct HTTP if {@code gh} is not installed or a CLI invocation fails; setting it to
 * {@code false} instead surfaces the failure so the misconfiguration is not silently ignored.
 */
@SuppressWarnings("unused")
public class CliExtension {

    private boolean enabled;
    private boolean fallback = true;

    /**
     * Controls whether GitHub REST calls are routed through the local {@code gh} CLI instead of
     * direct HTTP. Defaults to {@code false}.
     *
     * @param enabled whether to use the {@code gh} CLI for API calls.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return whether API calls are routed through the local {@code gh} CLI.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Controls whether the plugin falls back to direct HTTP when the {@code gh} CLI is enabled but
     * cannot be used (not installed, or a CLI invocation fails). Defaults to {@code true}. When set
     * to {@code false}, such a situation raises an error instead of silently switching to HTTP.
     *
     * @param fallback whether to fall back to HTTP when the CLI is unavailable or fails.
     */
    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }

    /**
     * @return whether the plugin falls back to HTTP when the CLI cannot be used.
     */
    public boolean isFallback() {
        return fallback;
    }
}
