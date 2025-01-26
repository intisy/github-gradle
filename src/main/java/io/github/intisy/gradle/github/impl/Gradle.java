package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.Logger;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Gradle {
    public static void modifyBuildFile(Project project, String searchString, String replacement) {
        File buildFile = project.getBuildFile();
        Logger logger = new Logger(project);
        logger.debug("Replacing " + searchString + " with " + replacement + " in " + buildFile.getAbsolutePath());
        if (!buildFile.exists()) {
            logger.warn("Build file not found: " + buildFile.getAbsolutePath());
            return;
        }
        try {
            String content = new String(Files.readAllBytes(buildFile.toPath()));
            String modifiedContent = content.replaceAll(searchString, replacement);
            Files.write(buildFile.toPath(), modifiedContent.getBytes());
        } catch (IOException e) {
            logger.error("File modification failed: " + e.getMessage());
        }
    }

    public static void safeSoftRefreshGradle(Project project) {
        Logger logger = new Logger(project);
        logger.log("Attempting safe configuration refresh...");
        project.getConfigurations().forEach(config -> {
            if (config.isCanBeResolved()) { // Only touch resolvable configs
                config.setTransitive(false);
                config.setTransitive(true); // Toggle to invalidate cache
            }
        });
        resolveIfPossible(project, "compileClasspath");
        resolveIfPossible(project, "runtimeClasspath");
        resolveIfPossible(project, "testRuntimeClasspath");
        project.getLogger().lifecycle(
                "For full refresh, run with: --refresh-dependencies --recompile-scripts"
        );
    }

    private static void resolveIfPossible(Project project, String configName) {
        try {
            project.getConfigurations()
                    .getByName(configName)
                    .getResolvedConfiguration()
                    .getResolvedArtifacts();
        } catch (Exception e) {
            project.getLogger().debug("Couldn't resolve {}: {}", configName, e.getMessage());
        }
    }
}
