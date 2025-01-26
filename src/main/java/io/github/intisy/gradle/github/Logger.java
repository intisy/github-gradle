package io.github.intisy.gradle.github;

import org.gradle.api.Project;

public class Logger {
    private final GithubExtension extension;
    private final Project project;
    public Logger(Project project) {
        this.project = project;
        this.extension = project.getExtensions().getByType(GithubExtension.class);
    }
    public void log(String message) {
        project.getLogger().lifecycle(message);
    }

    public void error(String message) {
        project.getLogger().error(message);
    }

    public void info(String message) {
        log(message);
    }

    public void debug(String message) {
        if (extension.isDebug()) {
            project.getLogger().lifecycle(message);
        }
    }

    public void warn(String message) {
        project.getLogger().warn(message);
    }
}
