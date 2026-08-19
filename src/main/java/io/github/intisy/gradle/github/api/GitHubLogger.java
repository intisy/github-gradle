package io.github.intisy.gradle.github.api;

/**
 * Receives diagnostic output from {@code GitHub} without requiring a Gradle {@code Project}.
 */
public interface GitHubLogger {
    /**
     * Records a normal, always-visible progress message (e.g. "Cloning repository...").
     *
     * @param message the message to record.
     */
    void log(String message);

    /**
     * Records a failure message with no associated exception.
     *
     * @param message the message to record.
     */
    void error(String message);

    /**
     * Records a failure message together with the exception that caused it.
     *
     * @param message the message to record.
     * @param throwable the exception that caused the failure.
     */
    void error(String message, Throwable throwable);

    /**
     * Records a verbose diagnostic message intended for troubleshooting, not routine output.
     *
     * @param message the message to record.
     */
    void debug(String message);

    /**
     * Records a recoverable-condition message (e.g. falling back to a cached jar after a rate limit).
     *
     * @param message the message to record.
     */
    void warn(String message);
}
