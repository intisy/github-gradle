package io.github.intisy.gradle.github;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;

public class Logger {
    private final GithubExtension extension;
    private final Project project;
    /**
     * Constructs a new instance of Logger using the provided {@link Project} instance.
     *
     * @param project The {@link Project} instance associated with this Logger.
     *
     * @throws NullPointerException If the {@code project} is null.
     *
     * @see GithubExtension
     */
    public Logger(Project project) {
        this(project.getExtensions().getByType(GithubExtension.class), project);
    }
    /**
     * Constructs a new instance of Logger.
     *
     * @param extension The {@link GithubExtension} instance to be used for logging configuration.
     * @param project The {@link Project} instance associated with this Logger.
     *
     * @throws NullPointerException If either {@code extension} or {@code project} is null.
     */
    public Logger(GithubExtension extension, Project project) {
        if (extension == null || project == null) {
            throw new NullPointerException("extension and project cannot be null");
        }
        this.extension = extension;
        this.project = project;
    }
    /**
     * Logs a message at the lifecycle level.
     *
     * @param message The message to be logged.
     */
    public void log(String message) {
        project.getLogger().lifecycle(message);
    }

    /**
     * Logs an error message.
     *
     * @param message The error message to be logged.
     */
    public void error(String message) {
        project.getLogger().error(message);
    }

    /**
     * Logs a debug message if the debug mode is enabled or the log level is INFO or DEBUG.
     *
     * @param message The debug message to be logged.
     */
    public void debug(String message) {
        LogLevel logLevel = project.getGradle().getStartParameter().getLogLevel();
        if (extension.isDebug() || logLevel.equals(LogLevel.INFO) || logLevel.equals(LogLevel.DEBUG)) {
            project.getLogger().lifecycle(message);
        }
    }

    /**
     * Logs a warning message.
     *
     * @param message The warning message to be logged.
     */
    public void warn(String message) {
        project.getLogger().warn(message);
    }
}
