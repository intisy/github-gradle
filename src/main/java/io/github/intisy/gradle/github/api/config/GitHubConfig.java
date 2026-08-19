package io.github.intisy.gradle.github.api.config;

import io.github.intisy.gradle.github.extension.AuthExtension;
import io.github.intisy.gradle.github.extension.CliExtension;
import io.github.intisy.gradle.github.extension.ResilienceExtension;

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
    AuthExtension getAuth();

    /**
     * @return the settings controlling whether API calls are routed through the {@code gh} CLI.
     */
    CliExtension getCli();

    /**
     * @return the settings controlling fallback behavior when the GitHub API rate limit is hit.
     */
    ResilienceExtension getResilience();
}
