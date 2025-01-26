package io.github.intisy.gradle.github.impl;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Gradle {
    public static void modifyBuildFile(Project project, String searchString, String replacement) {
        File buildFile = project.getBuildFile();
        if (!buildFile.exists()) {
            project.getLogger().warn("Build file not found: " + buildFile.getAbsolutePath());
            return;
        }

        try {
            String content = new String(Files.readAllBytes(buildFile.toPath()));
            String modifiedContent = content.replaceAll(searchString, replacement);
            Files.write(buildFile.toPath(), modifiedContent.getBytes());
        } catch (IOException e) {
            project.getLogger().error("File modification failed: " + e.getMessage());
        }
    }

    public static void softRefreshGradle(Project project) {
        project.getLogger().lifecycle("Attempting Gradle configuration refresh...");
        project.getRepositories().clear();
        project.getRepositories().addAll(project.getRepositories());
        project.getConfigurations().forEach(config -> {
            config.resolve();
        });
        project.getLogger().lifecycle(
                "For complete refresh, run: gradlew --refresh-dependencies --recompile-scripts"
        );
    }
}
