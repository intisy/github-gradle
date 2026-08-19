package io.github.intisy.gradle.github.api;

/**
 * Receives diagnostic output from {@code GitHub} without requiring a Gradle {@code Project}.
 */
public interface GitHubLogger {
    void log(String message);

    void error(String message);

    void error(String message, Throwable throwable);

    void debug(String message);

    void warn(String message);
}
