package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.log.ConsoleGitHubLogger;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.impl.github.GitHub;
import io.github.intisy.gradle.github.impl.source.BuildInvoker;
import io.github.intisy.gradle.github.impl.source.SourceBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final class FakeModuleBuildInvoker implements BuildInvoker {
        private final List<String> modules;
        private final List<File> buildRoots = new ArrayList<File>();

        FakeModuleBuildInvoker(String... modules) {
            this.modules = new ArrayList<String>();
            for (String module : modules) {
                this.modules.add(module);
            }
        }

        @Override
        public void invoke(File buildRoot) throws IOException {
            buildRoots.add(buildRoot);
            for (String module : modules) {
                File libsDir = new File(new File(new File(buildRoot, module), "build"), "libs");
                Files.createDirectories(libsDir.toPath());
                Files.write(new File(libsDir, module + ".jar").toPath(),
                        ("stub " + module).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /** An origin whose gradle root is a subdirectory, the shape every library in this ecosystem has. */
    private static File originWithGradleSubdirectory(File dir) throws IOException, GitAPIException {
        File origin = createOriginRepo(dir);
        assertTrue(new File(origin, "java").mkdirs());
        addCommit(origin, "java/settings.gradle", "rootProject.name = 'widget'");
        return origin;
    }

    @Test
    public void buildsEveryNamedModuleFromASubdirectoryGradleRootInOneInvocation(@TempDir File tempDir)
            throws IOException, GitAPIException {
        File origin = originWithGradleSubdirectory(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");

        FakeModuleBuildInvoker invoker = new FakeModuleBuildInvoker("routing", "contracts");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        List<File> jars = builder.buildFromGit(origin.toURI().toString(), null, "java",
                Arrays.asList("routing", "contracts"));

        assertEquals(1, invoker.buildRoots.size(), "one build serves every module");
        assertEquals("java", invoker.buildRoots.get(0).getName(), "the build runs in the gradle root");
        assertEquals(2, jars.size());
        assertTrue(jars.get(0).getName().endsWith("-routing.jar"), jars.get(0).getName());
        assertTrue(jars.get(1).getName().endsWith("-contracts.jar"), jars.get(1).getName());
        assertEquals("stub routing", readFile(jars.get(0)));
        assertEquals("stub contracts", readFile(jars.get(1)));
    }

    @Test
    public void aSecondCallForTheSameCommitReusesEveryCachedModuleJar(@TempDir File tempDir)
            throws IOException, GitAPIException {
        File origin = originWithGradleSubdirectory(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");

        FakeModuleBuildInvoker invoker = new FakeModuleBuildInvoker("routing", "contracts");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);
        List<String> modules = Arrays.asList("routing", "contracts");

        builder.buildFromGit(origin.toURI().toString(), null, "java", modules);
        builder.buildFromGit(origin.toURI().toString(), null, "java", modules);

        assertEquals(1, invoker.buildRoots.size(), "a cached commit rebuilds nothing");
    }

    @Test
    public void aMissingGradleDirectoryFailsWithTheDirectoryItLookedFor(@TempDir File tempDir)
            throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");

        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir,
                new FakeModuleBuildInvoker("routing"));

        IOException failure = assertThrows(IOException.class, () -> builder.buildFromGit(
                origin.toURI().toString(), null, "no-such-dir", Arrays.asList("routing")));
        assertTrue(failure.getMessage().contains("no-such-dir"), failure.getMessage());
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

        File jar = builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, null);

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

        builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, null);
        File secondJar = builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, null);

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

        File widgetJar = builder.buildFromSource(originA.toURI().toString(), "acme", "widget", null, null);
        File gadgetJar = builder.buildFromSource(originB.toURI().toString(), "acme", "gadget", null, null);

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

        File firstJar = builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, null);
        File secondJar = builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, null);

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
                () -> builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, null));

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
                () -> builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, null));

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

        builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, firstSha);

        assertEquals("initial content", readFile(new File(checkoutDir, "file.txt")));
        assertTrue(new File(cacheDir, "acme-widget-" + firstSha + ".jar").isFile());
    }

    @Test
    public void freshCallClonesFromTheGivenUrlWithoutAssumingGithub(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File cacheDir = new File(tempDir, "cache");
        assertTrue(cacheDir.mkdirs());

        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        File jar = builder.buildFromSource(origin.toURI().toString(), "acme", "widget", null, null);

        assertTrue(jar.isFile());
        assertEquals("initial content", readFile(new File(new File(cacheDir, "acme-widget"), "file.txt")));
    }

    @Test
    public void twoArgOverloadDerivesTheRepoNameFromTheCloneUrlsLastPathSegment(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        String firstSha = headSha(origin);
        addCommit(origin, "second.txt", "second content");
        File cacheDir = new File(tempDir, "cache");
        assertTrue(cacheDir.mkdirs());

        FakeBuildInvoker invoker = new FakeBuildInvoker(origin.getName() + "-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        File jar = builder.buildFromGit(origin.toURI().toString(), firstSha);

        assertTrue(jar.isFile());
        assertEquals(1, invoker.invocations);

        File[] checkoutDirs = cacheDir.listFiles(File::isDirectory);
        assertEquals(1, checkoutDirs.length, "exactly one checkout directory should have been created");
        assertEquals("initial content", readFile(new File(checkoutDirs[0], "file.txt")));
    }

    /**
     * Two distinct clone URLs whose trailing {@code owner/repo}-shaped path segments happen to be
     * identical (here, both end in {@code acme/widget}) must build from two genuinely separate
     * checkouts, not silently share one. {@link GitHub#doesRepoExist} only checks that a git
     * object database exists at a path; it never compares that checkout's {@code origin} remote
     * back against the URL that was requested, so a project resolving the same owner/repo-shaped
     * identity against two different hosts must not silently reuse whichever checkout happens to
     * exist.
     */
    @Test
    public void twoDistinctUrlsWithTheSameTrailingPathGetDifferentCheckouts(@TempDir File tempDir) throws IOException, GitAPIException {
        File originA = createOriginRepo(new File(new File(tempDir, "hostA"), "acme/widget"));
        File originB = createOriginRepo(new File(new File(tempDir, "hostB"), "acme/widget"));
        addCommit(originB, "second.txt", "second content, only on hostB");
        File cacheDir = new File(tempDir, "cache");
        assertTrue(cacheDir.mkdirs());

        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        File jarA = builder.buildFromGit(originA.toURI().toString(), null);
        File jarB = builder.buildFromGit(originB.toURI().toString(), null);

        assertEquals(2, invoker.invocations, "each distinct URL must trigger its own build");
        assertNotEquals(jarA, jarB);

        File[] checkoutDirs = cacheDir.listFiles(File::isDirectory);
        assertEquals(2, checkoutDirs.length, "two distinct URLs must resolve to two distinct checkouts");
    }

    /**
     * Pins all four ref shapes {@code sources { git { ref = ... } } } can name: the remote's
     * default branch (ref null), a tag, a commit sha, and a non-default branch. A fresh clone only
     * ever creates a local branch for the default branch, so a tag or commit sha (already fetched
     * into the object database) resolve directly, but a non-default branch exists solely as
     * {@code refs/remotes/origin/<branch>} until a local tracking branch is created for it.
     */
    @Test
    public void allFourRefShapesResolveCorrectly(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = new File(tempDir, "origin");
        PersonIdent author = new PersonIdent("Test", "test@example.com");
        String mainSha;
        String developSha;
        try (Git git = Git.init().setDirectory(origin).setInitialBranch("main").call()) {
            Files.write(new File(origin, "file.txt").toPath(), "main content".getBytes(StandardCharsets.UTF_8));
            git.add().addFilepattern(".").call();
            git.commit().setMessage("main commit").setAuthor(author).setCommitter(author).call();
            mainSha = git.getRepository().resolve("HEAD").getName();
            git.tag().setName("v1").setMessage("v1").setTagger(author).call();

            git.branchCreate().setName("develop").call();
            git.checkout().setName("develop").call();
            Files.write(new File(origin, "file.txt").toPath(), "develop content".getBytes(StandardCharsets.UTF_8));
            git.add().addFilepattern(".").call();
            git.commit().setMessage("develop commit").setAuthor(author).setCommitter(author).call();
            developSha = git.getRepository().resolve("HEAD").getName();
            git.checkout().setName("main").call();
        }

        File cacheDir = new File(tempDir, "cache");
        assertTrue(cacheDir.mkdirs());
        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);
        String cloneUrl = origin.toURI().toString();

        builder.buildFromSource(cloneUrl, "default-owner", "widget", null, null);
        assertEquals("main content", readFile(new File(new File(cacheDir, "default-owner-widget"), "file.txt")),
                "ref = null must resolve the remote's default branch");

        builder.buildFromSource(cloneUrl, "tag-owner", "widget", null, "v1");
        assertEquals("main content", readFile(new File(new File(cacheDir, "tag-owner-widget"), "file.txt")),
                "ref = a tag must resolve to the commit it points at");

        builder.buildFromSource(cloneUrl, "sha-owner", "widget", null, mainSha);
        assertEquals("main content", readFile(new File(new File(cacheDir, "sha-owner-widget"), "file.txt")),
                "ref = a commit sha must resolve directly");

        builder.buildFromSource(cloneUrl, "branch-owner", "widget", null, "develop");
        assertEquals("develop content", readFile(new File(new File(cacheDir, "branch-owner-widget"), "file.txt")),
                "ref = a non-default branch must resolve via its remote-tracking ref");
    }

    /**
     * When a repository carries both a tag and a non-default branch of the same name, porcelain
     * {@code git checkout <name>} resolves the branch, not the tag. {@link
     * org.eclipse.jgit.lib.Repository#resolve} alone does not have this precedence (it checks
     * {@code refs/tags/<name>} before {@code refs/heads/<name>}), so {@code checkOutRef} must try
     * the branch (local, then remote-tracking) first and only fall back to generic resolution.
     */
    @Test
    public void branchWinsOverATagWithTheSameName(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = new File(tempDir, "origin");
        PersonIdent author = new PersonIdent("Test", "test@example.com");
        try (Git git = Git.init().setDirectory(origin).setInitialBranch("main").call()) {
            Files.write(new File(origin, "file.txt").toPath(), "main content".getBytes(StandardCharsets.UTF_8));
            git.add().addFilepattern(".").call();
            git.commit().setMessage("main commit").setAuthor(author).setCommitter(author).call();
            git.tag().setName("v1").setMessage("v1").setTagger(author).call();

            git.branchCreate().setName("v1").call();
            git.checkout().setName("refs/heads/v1").call();
            Files.write(new File(origin, "file.txt").toPath(), "branch v1 content".getBytes(StandardCharsets.UTF_8));
            git.add().addFilepattern(".").call();
            git.commit().setMessage("branch v1 commit").setAuthor(author).setCommitter(author).call();
            git.checkout().setName("refs/heads/main").call();
        }

        File cacheDir = new File(tempDir, "cache");
        assertTrue(cacheDir.mkdirs());
        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        builder.buildFromSource(origin.toURI().toString(), "ambiguous-owner", "widget", null, "v1");

        assertEquals("branch v1 content", readFile(new File(new File(cacheDir, "ambiguous-owner-widget"), "file.txt")),
                "a branch must win over a tag of the same name, matching porcelain 'git checkout <name>'");
    }
    /**
     * F3's regression: {@code cloneRepositoryFromUrl} used to never call {@code setBranch} on the
     * clone command, so a first-ever clone of a non-default {@code branch} with no {@code
     * commitSha} silently produced a jar from the remote's default branch instead, with no error.
     * This exercises the {@code buildFromSource(cloneUrl, owner, repo, branch, commitSha)}
     * overload directly (the shape {@code GitHubSourceBuilds} uses), never going through {@link
     * SourceBuilder#buildFromGit}, since that method always passes {@code branch = null} and only
     * ever sets {@code commitSha}.
     */
    @Test
    public void firstEverCloneOfANonDefaultBranchWithANullShaBuildsFromThatBranch(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        String defaultBranch;
        try (Git git = Git.open(origin)) {
            defaultBranch = git.getRepository().getBranch();
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
        }
        addCommit(origin, "feature.txt", "on feature");
        try (Git git = Git.open(origin)) {
            git.checkout().setName(defaultBranch).call();
        }

        File cacheDir = new File(tempDir, "cache");
        assertTrue(cacheDir.mkdirs());
        FakeBuildInvoker invoker = new FakeBuildInvoker("widget-1.0.jar");
        SourceBuilder builder = new SourceBuilder(new GithubExtension(), LOGGER, cacheDir, invoker);

        builder.buildFromSource(origin.toURI().toString(), "first-ever-owner", "widget", "feature", null);

        File checkoutDir = new File(cacheDir, "first-ever-owner-widget");
        assertTrue(new File(checkoutDir, "feature.txt").isFile(),
                "a first-ever clone with branch = 'feature' and no commitSha must build from 'feature', "
                        + "not silently from the default branch");
    }
}

