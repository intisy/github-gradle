package io.github.intisy.gradle.github.impl;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Local, offline git fixtures shared by the {@code impl} package's hermetic tests.
 */
final class GitTestFixtures {
    private GitTestFixtures() {
    }

    static File createOriginRepo(File dir) throws IOException, GitAPIException {
        try (Git git = Git.init().setDirectory(dir).call()) {
            Files.write(new File(dir, "file.txt").toPath(), "initial content".getBytes(StandardCharsets.UTF_8));
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("initial commit")
                    .setAuthor("Test", "test@example.com")
                    .setCommitter("Test", "test@example.com")
                    .call();
        }
        return dir;
    }

    static void addCommit(File repoDir, String fileName, String content) throws IOException, GitAPIException {
        try (Git git = Git.open(repoDir)) {
            Files.write(new File(repoDir, fileName).toPath(), content.getBytes(StandardCharsets.UTF_8));
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("second commit")
                    .setAuthor("Test", "test@example.com")
                    .setCommitter("Test", "test@example.com")
                    .call();
        }
    }

    // GitHub.cloneRepository() always builds a hardcoded public-GitHub URL, ignoring resourcesExtension.repoUrl; hermetic fixtures clone via jgit directly.
    static void cloneLocally(File originDir, File cloneDir) throws GitAPIException {
        try (Git ignored = Git.cloneRepository()
                .setURI(originDir.toURI().toString())
                .setDirectory(cloneDir)
                .call()) {
        }
    }
}
