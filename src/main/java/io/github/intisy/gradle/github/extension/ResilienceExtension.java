package io.github.intisy.gradle.github.extension;

/**
 * Extension for configuring how the plugin behaves when GitHub cannot be reached as expected.
 *
 * <pre>
 * github {
 *     resilience {
 *         skipOnRateLimit = true
 *     }
 * }
 * </pre>
 */
@SuppressWarnings("unused")
public class ResilienceExtension {

    private boolean skipOnRateLimit;

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
     * @return whether rate-limited operations degrade gracefully instead of failing the build.
     */
    public boolean isSkipOnRateLimit() {
        return skipOnRateLimit;
    }
}
