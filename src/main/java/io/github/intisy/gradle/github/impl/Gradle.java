package io.github.intisy.gradle.github.impl;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

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

    public static void safeSoftRefreshGradle(Project project) {
        project.getLogger().lifecycle("Attempting safe configuration refresh...");
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
