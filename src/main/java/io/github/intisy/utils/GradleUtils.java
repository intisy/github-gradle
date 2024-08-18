package io.github.intisy.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class GradleUtils {
    public static Path getGradleHome() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".gradle", "caches", "github");
    }
}
