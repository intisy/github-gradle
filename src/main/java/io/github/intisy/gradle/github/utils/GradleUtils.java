package io.github.intisy.gradle.github.utils;

import org.gradle.api.Project;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * This utility class provides methods for interacting with Gradle.
 */
public class GradleUtils {
    /**
     * Returns the path to the Gradle home directory.
     *
     * <p>The Gradle home directory is located at {@code ~/.gradle/caches/github} by default.
     *
     * @return the path to the Gradle home directory
     */
    public static Path getGradleHome() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".gradle", "caches");
    }

    /**
     * Retrieves all projects recursively, starting from the specified project and including
     * its subprojects, if any.
     *
     * @param project the root project from which to begin collecting projects
     * @return a set containing the root project and all its subprojects
     */
    public static Set<Project> getAllProjectsRecursive(Project project) {
        Set<Project> projects = new HashSet<>();
        collectProjectsRecursive(project, projects);
        return projects;
    }

    /**
     * Recursively collects all subprojects of the specified project and adds them to the provided set.
     * This ensures that all hierarchical project relationships are traversed and included.
     *
     * @param project the project from which to start collecting subprojects
     * @param projects the set to which collected projects will be added
     */
    private static void collectProjectsRecursive(Project project, Set<Project> projects) {
        if (!projects.add(project))
            return;
        project.getSubprojects().forEach(p -> collectProjectsRecursive(p, projects));
    }
}
