package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.extension.AuthExtension;
import io.github.intisy.gradle.github.extension.CliExtension;
import io.github.intisy.gradle.github.extension.ResilienceExtension;

/**
 * Supplies the configuration {@code GitHub} needs, without requiring a Gradle build script.
 */
public interface GitHubConfig {
    String getAccessToken();

    AuthExtension getAuth();

    CliExtension getCli();

    ResilienceExtension getResilience();
}
