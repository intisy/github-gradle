package io.github.intisy.gradle.github;

import org.gradle.api.Project;

/**
 * A logger for the GitHub plugin.
 */
public class Logger {
    private static final String PREFIX = "[GitHub] ";
    private final GithubExtension extension;
    private final org.gradle.api.logging.Logger gradleLogger;

    /**
     * Creates a new logger.
     * @param extension The GitHub extension.
     */
    public Logger(GithubExtension extension) {
        this.extension = extension;
        this.gradleLogger = org.gradle.api.logging.Logging.getLogger(Logger.class);
    }

    /**
     * Creates a new logger.
     * @param project The project.
     */
    public Logger(Project project) {
        this(project.getExtensions().getByType(GithubExtension.class), project);
    }

    /**
     * Creates a new logger.
     * @param extension The GitHub extension.
     * @param project The project.
     */
    public Logger(GithubExtension extension, Project project) {
        if (extension == null || project == null) {
            throw new NullPointerException("extension and project cannot be null");
        }
        this.extension = extension;
        this.gradleLogger = project.getLogger();
    }

    /**
     * Logs a standard lifecycle message, visible in the default Gradle output.
     * @param message The message to log.
     */
    public void log(String message) {
        gradleLogger.lifecycle(PREFIX + message);
    }

    /**
     * Logs an error message.
     * @param message The message to log.
     */
    public void error(String message) {
        gradleLogger.error(PREFIX + message);
    }

    /**
     * Logs an error message along with an exception's stack trace.
     * @param message The message to log.
     * @param throwable The exception to log.
     */
    public void error(String message, Throwable throwable) {
        gradleLogger.error(PREFIX + message, throwable);
    }

    /**
     * Logs a debug message.
     * <p>
     * This message will be shown at the LIFECYCLE level (visible by default) only if
     * the user sets `github.debug = true` in their build script, providing an easy
     * way to enable verbose logging for this plugin specifically.
     * @param message The message to log.
     */
    public void debug(String message) {
        if (extension == null || extension.isDebug()) {
            gradleLogger.lifecycle(PREFIX + "[DEBUG] " + message);
        } else {
            gradleLogger.debug(PREFIX + message);
        }
    }

    /**
     * Logs a warning message.
     * @param message The message to log.
     */
    public void warn(String message) {
        gradleLogger.warn(PREFIX + message);
    }

    /**
     * Logs a warning message along with an exception's stack trace.
     * @param message The message to log.
     * @param throwable The exception to log.
     */
    public void warn(String message, Throwable throwable) {
        gradleLogger.warn(PREFIX + message, throwable);
    }
}