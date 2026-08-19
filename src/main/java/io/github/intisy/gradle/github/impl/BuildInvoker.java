package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.GitHubLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a build against a checked-out repository.
 *
 * <p>Injected into {@link SourceBuilder} so its caching logic can be tested without running a
 * real build.
 */
public interface BuildInvoker {
    void invoke(File checkoutDir) throws IOException;

    /**
     * Shells out to the checkout's own {@code gradlew} wrapper, so the build runs with whatever
     * Gradle version and plugin configuration the checkout itself declares.
     */
    final class Gradlew implements BuildInvoker {
        private final GitHubLogger logger;

        public Gradlew(GitHubLogger logger) {
            this.logger = logger;
        }

        @Override
        public void invoke(File checkoutDir) throws IOException {
            List<String> command = new ArrayList<String>();
            if (isWindows()) {
                command.add("cmd");
                command.add("/c");
                command.add("gradlew.bat");
            } else {
                command.add("./gradlew");
            }
            command.add("build");
            command.add("--console=plain");

            logger.debug("Invoking " + String.join(" ", command) + " in " + checkoutDir.getAbsolutePath());
            Process process = new ProcessBuilder(command)
                    .directory(checkoutDir)
                    .redirectErrorStream(true)
                    .start();
            drain(process.getInputStream());

            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for gradlew build in "
                        + checkoutDir.getAbsolutePath(), e);
            }
            if (exitCode != 0) {
                throw new IOException("gradlew build failed (exit " + exitCode + ") in "
                        + checkoutDir.getAbsolutePath());
            }
        }

        private static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase().contains("win");
        }

        // Discards the process's combined stdout/stderr; without this the pipe buffer can fill and the process hangs.
        private static void drain(InputStream input) throws IOException {
            byte[] buffer = new byte[8192];
            while (input.read(buffer) != -1) {
            }
        }
    }
}
