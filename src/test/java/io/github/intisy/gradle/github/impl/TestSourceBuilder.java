package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.log.ConsoleGitHubLogger;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.impl.source.BuildInvoker;
import io.github.intisy.gradle.github.impl.source.SourceBuilder;
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

import static io.github.intisy.gradle.github.impl.GitTestFixtures.addCommit;
import static io.github.intisy.gradle.github.impl.GitTestFixtures.cloneLocally;
import static io.github.intisy.gradle.github.impl.GitTestFixtures.createOriginRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSourceBuilder {
    private static final GitHubLogger LOGGER = new ConsoleGitHubLogger(false);

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

        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        File jar = builder.buildFromSource("acme", "widget", null, null);

        assertEquals(1, invoker.invocations);
        assertEquals(new File(cacheDir, "acme-widget-" + headSha + ".jar"), jar);
        assertTrue(jar.isFile());
        assertEquals("stub jar", readFile(jar));
    }

    @Test
    public void secondCallSucceedsWithoutAnyRepositoryPreconfigured(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");
        File checkoutDir = new File(cacheDir, "acme-widget");
        assertTrue(cacheDir.mkdirs());
        cloneLocally(origin, checkoutDir);

        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        builder.buildFromSource("acme", "widget", null, null);
        File secondJar = builder.buildFromSource("acme", "widget", null, null);

        assertTrue(secondJar.isFile());
        assertEquals(1, invoker.invocations);
    }

    @Test
    public void differentRepositoriesCanBeBuiltFromTheSameSourceBuilderInstance(@TempDir File tempDir) throws IOException, GitAPIException {
        File originA = createOriginRepo(new File(tempDir, "originA"));
        File originB = createOriginRepo(new File(tempDir, "originB"));
        File cacheDir = new File(tempDir, "cache");
        assertTrue(cacheDir.mkdirs());
        cloneLocally(originA, new File(cacheDir, "acme-widget"));
        cloneLocally(originB, new File(cacheDir, "acme-gadget"));

        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar", "gadget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        File widgetJar = builder.buildFromSource("acme", "widget", null, null);
        File gadgetJar = builder.buildFromSource("acme", "gadget", null, null);

        assertEquals(2, invoker.invocations);
        assertTrue(widgetJar.isFile());
        assertTrue(gadgetJar.isFile());
        assertNotEquals(widgetJar, gadgetJar);
    }

    @Test
    public void secondCallForTheSameShaReturnsTheCachedJarWithoutRebuilding(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");
        File checkoutDir = new File(cacheDir, "acme-widget");
        assertTrue(cacheDir.mkdirs());
        cloneLocally(origin, checkoutDir);

        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

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

        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar", "widget-2.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

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

        FakeBuildInvoker invoker = new FakeBuildInvoker();
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

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

        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        builder.buildFromSource("acme", "widget", null, firstSha);

        assertEquals("initial content", readFile(new File(checkoutDir, "file.txt")));
        assertTrue(new File(cacheDir, "acme-widget-" + firstSha + ".jar").isFile());
    }
}
