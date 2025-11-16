package io.github.intisy.gradle.github.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Author: Finn Birich
 */
public class FileUtils {
    /**
     * Copies a directory from the source path to the destination path.
     *
     * @param sourceDir the path to the source directory
     * @param destDir the path to the destination directory
     * @throws IOException if an I/O error occurs
     */
    public static void copyDirectory(Path sourceDir, Path destDir) throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Source directory does not exist or is not a directory: " + sourceDir);
        }

        try (Stream<Path> files = Files.walk(sourceDir)) {
            files.forEach(sourcePath -> {
                try {
                    if (sourcePath.toString().contains(".git")) {
                        return;
                    }
                    Path targetPath = destDir.resolve(sourceDir.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        if (!Files.exists(targetPath)) {
                            Files.createDirectories(targetPath);
                        }
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error copying directory", e);
                }
            });
        }
    }

    /**
     * Deletes a directory and all its contents.
     *
     * @param dir the path to the directory to delete
     * @throws IOException if an I/O error occurs
     */
    public static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Error deleting directory", e);
                        }
                    });
            }
        }
    }
}
