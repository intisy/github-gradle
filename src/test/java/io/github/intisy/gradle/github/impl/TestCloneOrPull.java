package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCloneOrPull {
    private GitHub makeGitHub() {
        GithubExtension ext = new GithubExtension();
        ResourcesExtension res = new ResourcesExtension();
        Logger logger = new Logger(ext);
        return new GitHub(logger, res, ext);
    }

    private GitHub makeGitHub(String repoUrl) {
        GithubExtension ext = new GithubExtension();
        ResourcesExtension res = new ResourcesExtension();
        res.setRepoUrl(repoUrl);
        Logger logger = new Logger(ext);
        return new GitHub(logger, res, ext);
    }

    private File createOriginRepo(File dir) throws IOException, GitAPIException {
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

    private void addCommit(File repoDir, String fileName, String content) throws IOException, GitAPIException {
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
    private void cloneLocally(File originDir, File cloneDir) throws GitAPIException {
        try (Git ignored = Git.cloneRepository()
                .setURI(originDir.toURI().toString())
                .setDirectory(cloneDir)
                .call()) {
        }
    }

    private String currentBranch(File repoDir) throws IOException {
        try (Git git = Git.open(repoDir)) {
            return git.getRepository().getBranch();
        }
    }

    @Test
    public void testDoesRepoExistFalseForEmptyDirectory(@TempDir File tempDir) {
        GitHub gh = makeGitHub();
        assertFalse(gh.doesRepoExist(tempDir));
    }

    @Test
    public void testDoesRepoExistTrueAfterCloneWithCommittedFile(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File clone = new File(tempDir, "clone");
        cloneLocally(origin, clone);

        GitHub gh = makeGitHub();
        assertTrue(gh.doesRepoExist(clone));
        byte[] content = Files.readAllBytes(new File(clone, "file.txt").toPath());
        assertEquals("initial content", new String(content, StandardCharsets.UTF_8));
    }

    @Test
    public void testIsRepoUpToDateTrueImmediatelyAfterClone(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File clone = new File(tempDir, "clone");
        cloneLocally(origin, clone);

        GitHub gh = makeGitHub(origin.toURI().toString());
        assertTrue(gh.isRepoUpToDate(clone));
    }

    @Test
    public void testIsRepoUpToDateFalseAfterOriginGetsNewCommit(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File clone = new File(tempDir, "clone");
        cloneLocally(origin, clone);
        addCommit(origin, "second.txt", "second content");

        GitHub gh = makeGitHub(origin.toURI().toString());
        assertFalse(gh.isRepoUpToDate(clone));
    }

    @Test
    public void testPullRepositoryBringsNewContentIntoClone(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File clone = new File(tempDir, "clone");
        cloneLocally(origin, clone);
        addCommit(origin, "second.txt", "second content");

        GitHub gh = makeGitHub(origin.toURI().toString());
        gh.pullRepository(clone);

        byte[] content = Files.readAllBytes(new File(clone, "second.txt").toPath());
        assertEquals("second content", new String(content, StandardCharsets.UTF_8));
        assertTrue(gh.isRepoUpToDate(clone));
    }

    @Test
    public void testPullRepositoryWithExplicitBranchBringsNewContentIntoClone(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File clone = new File(tempDir, "clone");
        cloneLocally(origin, clone);
        addCommit(origin, "second.txt", "second content");

        GitHub gh = makeGitHub(origin.toURI().toString());
        gh.pullRepository(clone, currentBranch(clone));

        byte[] content = Files.readAllBytes(new File(clone, "second.txt").toPath());
        assertEquals("second content", new String(content, StandardCharsets.UTF_8));
    }

    @Test
    public void testCloneOrPullRepositoryPullsIntoExistingCloneWithIdenticalContent(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File clone = new File(tempDir, "clone");
        cloneLocally(origin, clone);
        addCommit(origin, "second.txt", "second content");

        GitHub gh = makeGitHub(origin.toURI().toString());
        gh.cloneOrPullRepository(clone, "unused-owner", "unused-repo", null);

        byte[] originContent = Files.readAllBytes(new File(origin, "second.txt").toPath());
        byte[] cloneContent = Files.readAllBytes(new File(clone, "second.txt").toPath());
        assertArrayEquals(originContent, cloneContent);
    }

    @Test
    public void testCloneOrPullRepositoryNoopWhenAlreadyUpToDate(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File clone = new File(tempDir, "clone");
        cloneLocally(origin, clone);

        GitHub gh = makeGitHub(origin.toURI().toString());
        gh.cloneOrPullRepository(clone, "unused-owner", "unused-repo", null);

        byte[] content = Files.readAllBytes(new File(clone, "file.txt").toPath());
        assertEquals("initial content", new String(content, StandardCharsets.UTF_8));
        assertTrue(gh.isRepoUpToDate(clone));
    }
}
