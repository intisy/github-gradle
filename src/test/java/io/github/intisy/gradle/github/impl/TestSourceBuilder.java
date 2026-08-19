package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.ConsoleGitHubLogger;
import io.github.intisy.gradle.github.api.GitHubLogger;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSourceBuilder {
    private static final GitHubLogger LOGGER = new ConsoleGitHubLogger(false);

    private GitHub makeGitHub(String repoUrl) {
        GithubExtension ext = new GithubExtension();
        ResourcesExtension res = new ResourcesExtension();
        res.setRepoUrl(repoUrl);
        return new GitHub(LOGGER, res, ext);
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

    // GitHub.cloneOrPull's initial-clone branch builds a hardcoded public-GitHub URL (see Task 2), so
    // fixtures pre-populate the checkout with a direct jgit clone and only ever exercise the pull path.
    private void cloneLocally(File originDir, File cloneDir) throws GitAPIException {
        try (Git ignored = Git.cloneRepository()
                .setURI(originDir.toURI().toString())
                .setDirectory(cloneDir)
                .call()) {
        }
    }

    private String headSha(File repoDir) throws IOException {
        try (Git git = Git.open(repoDir)) {
            return git.getRepository().resolve("HEAD").getName();
        }
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static final class FakeBuildInvoker implements BuildInvoker {
        private final List<String> jarNames;
        private int invocations = 0;

        FakeBuildInvoker(String... jarNames) {
            this.jarNames = new ArrayList<String>();
            for (String jarName : jarNames) {
                this.jarNames.add(jarName);
            }
        }

        @Override
        public void invoke(File checkoutDir) throws IOException {
            invocations++;
            File libsDir = new File(new File(checkoutDir, "build"), "libs");
            Files.createDirectories(libsDir.toPath());
            for (String jarName : jarNames) {
                Files.write(new File(libsDir, jarName).toPath(), "stub jar".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    public void firstCallInvokesTheBuildOnceAndReturnsTheCachedJar(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");
        File checkoutDir = new File(cacheDir, "acme-widget");
        assertTrue(cacheDir.mkdirs());
        cloneLocally(origin, checkoutDir);
        String headSha = headSha(checkoutDir);

        GitHub gh = makeGitHub(origin.toURI().toString());
        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(gh, LOGGER, cacheDir, invoker);

        File jar = builder.buildFromSource("acme", "widget", null, null);

        assertEquals(1, invoker.invocations);
        assertEquals(new File(cacheDir, "acme-widget-" + headSha + ".jar"), jar);
        assertTrue(jar.isFile());
        assertEquals("stub jar", readFile(jar));
    }

    @Test
    public void secondCallForTheSameShaReturnsTheCachedJarWithoutRebuilding(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");
        File checkoutDir = new File(cacheDir, "acme-widget");
        assertTrue(cacheDir.mkdirs());
        cloneLocally(origin, checkoutDir);

        GitHub gh = makeGitHub(origin.toURI().toString());
        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(gh, LOGGER, cacheDir, invoker);

        File firstJar = builder.buildFromSource("acme", "widget", null, null);
        File secondJar = builder.buildFromSource("acme", "widget", null, null);

        assertEquals(1, invoker.invocations);
        assertEquals(firstJar, secondJar);
    }

    @Test
    public void twoCandidateJarsThrowNamingBoth(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");
        File checkoutDir = new File(cacheDir, "acme-widget");
        assertTrue(cacheDir.mkdirs());
        cloneLocally(origin, checkoutDir);

        GitHub gh = makeGitHub(origin.toURI().toString());
        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar", "widget-2.0.jar");
        SourceBuilder builder = new SourceBuilder(gh, LOGGER, cacheDir, invoker);

        IOException exception = assertThrows(IOException.class,
                () -> builder.buildFromSource("acme", "widget", null, null));

        assertTrue(exception.getMessage().contains("widget-1.0.jar"));
        assertTrue(exception.getMessage().contains("widget-2.0.jar"));
    }

    @Test
    public void noJarThrowsNamingTheSearchedDirectory(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");
        File checkoutDir = new File(cacheDir, "acme-widget");
        assertTrue(cacheDir.mkdirs());
        cloneLocally(origin, checkoutDir);

        GitHub gh = makeGitHub(origin.toURI().toString());
        FakeBuildInvoker invoker = new FakeBuildInvoker();
        SourceBuilder builder = new SourceBuilder(gh, LOGGER, cacheDir, invoker);

        IOException exception = assertThrows(IOException.class,
                () -> builder.buildFromSource("acme", "widget", null, null));

        File expectedLibsDir = new File(new File(checkoutDir, "build"), "libs");
        assertTrue(exception.getMessage().contains(expectedLibsDir.getAbsolutePath()));
    }

    @Test
    public void nonNullCommitShaChecksOutThatCommit(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        String firstSha = headSha(origin);
        addCommit(origin, "file.txt", "second content");

        File cacheDir = new File(tempDir, "cache");
        File checkoutDir = new File(cacheDir, "acme-widget");
        assertTrue(cacheDir.mkdirs());
        cloneLocally(origin, checkoutDir);
        assertEquals("second content", readFile(new File(checkoutDir, "file.txt")));

        GitHub gh = makeGitHub(origin.toURI().toString());
        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(gh, LOGGER, cacheDir, invoker);

        builder.buildFromSource("acme", "widget", null, firstSha);

        assertEquals("initial content", readFile(new File(checkoutDir, "file.txt")));
        assertTrue(new File(cacheDir, "acme-widget-" + firstSha + ".jar").isFile());
    }
}
