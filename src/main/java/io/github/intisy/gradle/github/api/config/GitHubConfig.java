package io.github.intisy.gradle.github.api.config;

import java.io.File;

/**
 * Supplies the configuration {@code GitHub} needs, without requiring a Gradle build script.
 */
public interface GitHubConfig {
    /**
     * @return the deprecated single-value token or SSH key, or null if unset. Kept for backward
     * compatibility; new configuration should use {@link #getAuth()}.
     */
    String getAccessToken();

    /**
     * @return the structured token/SSH key configuration, preferred over {@link #getAccessToken()}.
     */
    AuthSettings getAuth();

    /**
     * @return the settings controlling whether API calls are routed through the {@code gh} CLI.
     */
    CliSettings getCli();

    /**
     * @return the settings controlling fallback behavior when the GitHub API rate limit is hit.
     */
    ResilienceSettings getResilience();

    /**
     * @return a new {@link Builder} for assembling a {@link GitHubConfig} outside a Gradle build
     * script. Every builder method is optional; {@link Builder#build()} with none called produces
     * a config for fully anonymous, unauthenticated access.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Assembles an immutable {@link GitHubConfig} by composing {@link AuthSettings},
     * {@link CliSettings} and {@link ResilienceSettings}, the same settings types the Gradle DSL
     * configures.
     *
     * @implNote Each {@link #build()} call snapshots this builder's current state into fresh copies
     * of the three settings objects, and the built config hands out a fresh copy from every getter
     * call in turn. So further calls to this builder never affect an already-built config, and
     * mutating a settings object obtained from a built config never affects that config either.
     */
    final class Builder {
        private final AuthSettings authSettings = new AuthSettings();
        private final CliSettings cliSettings = new CliSettings();
        private final ResilienceSettings resilienceSettings = new ResilienceSettings();

        private Builder() {
        }

        /**
         * @param token a GitHub Personal Access Token, used for REST calls and HTTPS git operations.
         * @return this builder.
         */
        public Builder token(String token) {
            authSettings.setToken(token);
            return this;
        }

        /**
         * @param tokenFile a file whose contents are a GitHub token, used when {@link #token(String)} is not set.
         * @return this builder.
         */
        public Builder tokenFile(File tokenFile) {
            authSettings.setTokenFile(tokenFile);
            return this;
        }

        /**
         * @param sshKey the SSH private key file used for git clone/pull over SSH.
         * @return this builder.
         */
        public Builder sshKey(File sshKey) {
            authSettings.setSshKey(sshKey);
            return this;
        }

        /**
         * @param enabled whether to route GitHub REST calls through the local {@code gh} CLI.
         * @return this builder.
         */
        public Builder cliEnabled(boolean enabled) {
            cliSettings.setEnabled(enabled);
            return this;
        }

        /**
         * @param fallback whether to fall back to HTTP when {@code gh} is unavailable or fails.
         * @return this builder.
         */
        public Builder cliFallback(boolean fallback) {
            cliSettings.setFallback(fallback);
            return this;
        }

        /**
         * @param skipOnRateLimit whether to degrade gracefully (rather than fail) when the rate limit is hit.
         * @return this builder.
         */
        public Builder skipOnRateLimit(boolean skipOnRateLimit) {
            resilienceSettings.setSkipOnRateLimit(skipOnRateLimit);
            return this;
        }

        /**
         * @return an immutable {@link GitHubConfig} snapshot of this builder's current state.
         */
        public GitHubConfig build() {
            return new Impl(authSettings, cliSettings, resilienceSettings);
        }

        private static final class Impl implements GitHubConfig {
            private final AuthSettings authSettings;
            private final CliSettings cliSettings;
            private final ResilienceSettings resilienceSettings;

            private Impl(AuthSettings authSettings, CliSettings cliSettings, ResilienceSettings resilienceSettings) {
                this.authSettings = copyOf(authSettings);
                this.cliSettings = copyOf(cliSettings);
                this.resilienceSettings = copyOf(resilienceSettings);
            }

            @Override
            public String getAccessToken() {
                return null;
            }

            @Override
            public AuthSettings getAuth() {
                return copyOf(authSettings);
            }

            @Override
            public CliSettings getCli() {
                return copyOf(cliSettings);
            }

            @Override
            public ResilienceSettings getResilience() {
                return copyOf(resilienceSettings);
            }

            private static AuthSettings copyOf(AuthSettings source) {
                AuthSettings copy = new AuthSettings();
                copy.setToken(source.getToken());
                copy.setTokenFile(source.getTokenFile());
                copy.setSshKey(source.getSshKey());
                return copy;
            }

            private static CliSettings copyOf(CliSettings source) {
                CliSettings copy = new CliSettings();
                copy.setEnabled(source.isEnabled());
                copy.setFallback(source.isFallback());
                return copy;
            }

            private static ResilienceSettings copyOf(ResilienceSettings source) {
                ResilienceSettings copy = new ResilienceSettings();
                copy.setSkipOnRateLimit(source.isSkipOnRateLimit());
                return copy;
            }
        }
    }
}
