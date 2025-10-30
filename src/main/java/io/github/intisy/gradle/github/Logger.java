package io.github.intisy.gradle.github;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;

public class Logger {
    private final GithubExtension extension;
    private final Project project;

    public Logger(Project project) {
        this(project.getExtensions().getByType(GithubExtension.class), project);
    }

    public Logger(GithubExtension extension, Project project) {
        if (extension == null || project == null) {
            throw new NullPointerException("extension and project cannot be null");
        }
        this.extension = extension;
        this.project = project;
    }

    public void log(String message) {
        project.getLogger().lifecycle(message);
    }

    public void error(String message) {
        project.getLogger().error(message);
    }

    public void debug(String message) {
        LogLevel logLevel = project.getGradle().getStartParameter().getLogLevel();
        if (extension.isDebug() || logLevel.equals(LogLevel.INFO) || logLevel.equals(LogLevel.DEBUG)) {
            project.getLogger().lifecycle(message);
        }
    }

    public void warn(String message) {
        project.getLogger().warn(message);
    }
}
