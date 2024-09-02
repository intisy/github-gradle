package io.github.intisy.gradle.github.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

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
        return Paths.get(userHome, ".gradle", "caches", "github");
    }
}
