package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.capability.Downloads;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.impl.download.UrlDownloads;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.plugin.extension.SourcesExtension;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ProjectBuilder} projects never fire {@code afterEvaluate} on their own, so
 * {@link SourcesResolution#apply} needs the project forced through evaluation to run at all,
 * exactly like {@link TestDependencyResolution}. Most tests here stub {@link SourceBuilds} and
 * {@link Downloads} so they stay offline; {@link #jarEntrySha256IsGenuinelyVerifiedThroughTheRealDownloadsImplementation}
 * is the exception, driving the real {@link UrlDownloads} (against a canned, offline OkHttp
 * interceptor, never a real socket) to prove {@code sha256} is not just passed through but
 * genuinely enforced.
 */
public class TestSourcesResolution {

    @Test
    public void gitEntryResolvesIntoItsConfiguredNativeConfiguration() throws IOException {
        Project project = ProjectBuilder.builder().withName("sources-git-test").build();
        project.getPluginManager().apply("java");

        SourcesExtension sources = new SourcesExtension();
        sources.git(entry -> {
            entry.setUrl("https://gitlab.com/me/lib.git");
            entry.setRef("main");
        });

        File fakeJar = File.createTempFile("fake-git-source", ".jar");
        fakeJar.deleteOnExit();

        SourcesResolution.apply(project, new Logger(new GithubExtension(), project), sources,
                stubSourceBuilds(fakeJar, "https://gitlab.com/me/lib.git", "main"), unusedDownloads(), new HashSet<File>());

        ((ProjectInternal) project).evaluate();

        Set<Dependency> implementationDependencies = project.getConfigurations().getByName("implementation").getDependencies();
        assertEquals(1, implementationDependencies.size());
    }

    @Test
    public void jarEntryDefaultsIntoImplementationAndHonoursAnExplicitInto() throws IOException {
        Project project = ProjectBuilder.builder().withName("sources-jar-into-test").build();
        project.getPluginManager().apply("java-library");

        SourcesExtension sources = new SourcesExtension();
        sources.jar(entry -> entry.setUrl("https://nexus.internal/libs/foo-1.0.jar"));
        sources.jar(entry -> {
            entry.setUrl("https://nexus.internal/libs/bar-1.0.jar");
            entry.setInto("api");
        });

        File defaultJar = File.createTempFile("fake-jar-source-default", ".jar");
        File apiJar = File.createTempFile("fake-jar-source-api", ".jar");
        defaultJar.deleteOnExit();
        apiJar.deleteOnExit();

        Downloads downloads = (jarUrl, headers, sha256) -> {
            if (jarUrl.endsWith("foo-1.0.jar")) {
                return defaultJar;
            }
            if (jarUrl.endsWith("bar-1.0.jar")) {
                return apiJar;
            }
            throw new IllegalArgumentException("unexpected url: " + jarUrl);
        };

        SourcesResolution.apply(project, new Logger(new GithubExtension(), project), sources,
                unusedSourceBuilds(), downloads, new HashSet<File>());

        ((ProjectInternal) project).evaluate();

        assertEquals(1, project.getConfigurations().getByName("implementation").getDependencies().size(),
                "a jar entry with no explicit 'into' must default to 'implementation'");
        assertEquals(1, project.getConfigurations().getByName("api").getDependencies().size(),
                "a jar entry with an explicit 'into' must be added to that configuration");
    }

    /**
     * The DSL-level proof that {@code sha256} actually reaches {@link Downloads#download}: a
     * stubbed {@link Downloads} records the exact argument it was called with, and this asserts it
     * equals what {@code JarSourceEntry.sha256} was configured to.
     */
    @Test
    public void jarEntrySha256ReachesDownloads() throws IOException {
        Project project = ProjectBuilder.builder().withName("sources-jar-sha256-test").build();
        project.getPluginManager().apply("java");

        SourcesExtension sources = new SourcesExtension();
        sources.jar(entry -> {
            entry.setUrl("https://nexus.internal/libs/foo-1.0.jar");
            entry.setSha256("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d");
        });

        File fakeJar = File.createTempFile("fake-jar-source-sha256", ".jar");
        fakeJar.deleteOnExit();

        String[] capturedSha256 = new String[1];
        Downloads downloads = (jarUrl, headers, sha256) -> {
            capturedSha256[0] = sha256;
            return fakeJar;
        };

        SourcesResolution.apply(project, new Logger(new GithubExtension(), project), sources,
                unusedSourceBuilds(), downloads, new HashSet<File>());

        ((ProjectInternal) project).evaluate();

        assertEquals("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d", capturedSha256[0],
                "the sha256 configured on the jar { } entry must reach Downloads#download unchanged");
    }

    /**
     * The DSL-level proof that headers added via {@code header(name, value)} actually reach
     * {@link Downloads#download}.
     */
    @Test
    public void jarEntryHeadersReachDownloads() throws IOException {
        Project project = ProjectBuilder.builder().withName("sources-jar-headers-test").build();
        project.getPluginManager().apply("java");

        SourcesExtension sources = new SourcesExtension();
        sources.jar(entry -> {
            entry.setUrl("https://nexus.internal/libs/foo-1.0.jar");
            entry.header("Authorization", "Bearer my-token");
        });

        File fakeJar = File.createTempFile("fake-jar-source-headers", ".jar");
        fakeJar.deleteOnExit();

        Map<String, String>[] capturedHeaders = new Map[1];
        Downloads downloads = (jarUrl, headers, sha256) -> {
            capturedHeaders[0] = headers;
            return fakeJar;
        };

        SourcesResolution.apply(project, new Logger(new GithubExtension(), project), sources,
                unusedSourceBuilds(), downloads, new HashSet<File>());

        ((ProjectInternal) project).evaluate();

        assertEquals("Bearer my-token", capturedHeaders[0].get("Authorization"));
    }

    /**
     * Pins the same {@code addedJars} filter {@link DependencyResolution} uses: a jar reachable
     * through a {@code githubImplementation} coordinate AND a {@code sources { jar { } } } entry
     * must be added to the native configuration only once.
     */
    @Test
    public void jarReachableThroughBothGithubAndSourcesIsAddedOnlyOnce() throws IOException {
        Project project = ProjectBuilder.builder().withName("dedup-github-and-sources-test").build();
        project.getPluginManager().apply("java");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0");

        File sharedJar = File.createTempFile("shared-jar-across-github-and-sources", ".jar");
        sharedJar.deleteOnExit();

        SourcesExtension sources = new SourcesExtension();
        sources.jar(entry -> entry.setUrl("https://nexus.internal/libs/shared.jar"));

        GithubExtension githubExtension = new GithubExtension();
        Logger logger = new Logger(githubExtension, project);
        Set<File> addedJars = new HashSet<File>();

        DependencyResolution.apply(project, logger, githubExtension, new io.github.intisy.gradle.github.api.capability.Releases() {
            public String latestVersion(String owner, String repo) { throw new UnsupportedOperationException(); }
            public io.github.intisy.gradle.github.api.model.Release releaseByTag(String owner, String repo, String tag) { throw new UnsupportedOperationException(); }
            public io.github.intisy.gradle.github.api.model.Release latestRelease(String owner, String repo) { throw new UnsupportedOperationException(); }
            public java.util.Optional<File> downloadJar(String owner, String repo, String version) { throw new UnsupportedOperationException(); }
            public java.util.Optional<File> downloadJar(String owner, String repo, String version, String classifier) { throw new UnsupportedOperationException(); }
            public java.util.List<File> downloadAllModuleJars(String owner, String repo, String version) { throw new UnsupportedOperationException(); }
            public java.util.List<File> resolveWithDependencies(String owner, String repo, String version) {
                return java.util.Collections.singletonList(sharedJar);
            }
            public java.util.List<io.github.intisy.gradle.github.api.model.DeclaredDependency> declaredDependencies(File jar) { throw new UnsupportedOperationException(); }
        }, addedJars);

        SourcesResolution.apply(project, logger, sources, unusedSourceBuilds(),
                (jarUrl, headers, sha256) -> sharedJar, addedJars);

        ((ProjectInternal) project).evaluate();

        assertEquals(1, project.getConfigurations().getByName("implementation").getDependencies().size(),
                "the same jar reached via a github coordinate and a sources { jar { } } entry "
                        + "must be added exactly once across both resolutions");
    }

    /**
     * The end-to-end proof, using the real {@link UrlDownloads} rather than a stub, that a
     * {@code sha256} configured on a {@code sources { jar { } } } entry is genuinely verified: a
     * mismatched hash fails the whole project evaluation, and the matching hash succeeds.
     */
    @Test
    public void jarEntrySha256IsGenuinelyVerifiedThroughTheRealDownloadsImplementation(@TempDir File cacheDir) {
        byte[] jarBytes = "jar-content".getBytes(StandardCharsets.UTF_8);
        String correctSha256 = sha256Hex(jarBytes);
        String wrongSha256 = sha256Hex("not the jar content".getBytes(StandardCharsets.UTF_8));

        Project mismatchProject = ProjectBuilder.builder().withName("sources-jar-sha256-mismatch-test").build();
        mismatchProject.getPluginManager().apply("java");
        SourcesExtension mismatchSources = new SourcesExtension();
        mismatchSources.jar(entry -> {
            entry.setUrl("https://example.com/foo.jar");
            entry.setSha256(wrongSha256);
        });
        Downloads mismatchDownloads = new UrlDownloads(clientReturningCannedJar(jarBytes),
                new Logger(new GithubExtension(), mismatchProject), new File(cacheDir, "mismatch"));
        SourcesResolution.apply(mismatchProject, new Logger(new GithubExtension(), mismatchProject), mismatchSources,
                unusedSourceBuilds(), mismatchDownloads, new HashSet<File>());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> ((ProjectInternal) mismatchProject).evaluate());
        assertTrue(collectMessages(thrown).contains("sha256 mismatch"),
                "a wrong sha256 must fail project evaluation with a sha256 mismatch, got: " + collectMessages(thrown));

        Project matchProject = ProjectBuilder.builder().withName("sources-jar-sha256-match-test").build();
        matchProject.getPluginManager().apply("java");
        SourcesExtension matchSources = new SourcesExtension();
        matchSources.jar(entry -> {
            entry.setUrl("https://example.com/foo.jar");
            entry.setSha256(correctSha256);
        });
        Downloads matchDownloads = new UrlDownloads(clientReturningCannedJar(jarBytes),
                new Logger(new GithubExtension(), matchProject), new File(cacheDir, "match"));
        SourcesResolution.apply(matchProject, new Logger(new GithubExtension(), matchProject), matchSources,
                unusedSourceBuilds(), matchDownloads, new HashSet<File>());

        ((ProjectInternal) matchProject).evaluate();
        assertEquals(1, matchProject.getConfigurations().getByName("implementation").getDependencies().size(),
                "the matching sha256 must let the jar through");
    }

    @Test
    public void gitEntryMissingUrlFailsFast() {
        Project project = ProjectBuilder.builder().withName("sources-git-missing-url-test").build();
        project.getPluginManager().apply("java");

        SourcesExtension sources = new SourcesExtension();
        sources.git(entry -> entry.setRef("main"));

        SourcesResolution.apply(project, new Logger(new GithubExtension(), project), sources,
                unusedSourceBuilds(), unusedDownloads(), new HashSet<File>());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> ((ProjectInternal) project).evaluate());
        assertTrue(collectMessages(thrown).contains("url"));
    }

    @Test
    public void jarEntryMissingUrlFailsFast() {
        Project project = ProjectBuilder.builder().withName("sources-jar-missing-url-test").build();
        project.getPluginManager().apply("java");

        SourcesExtension sources = new SourcesExtension();
        sources.jar(entry -> entry.setSha256("abc"));

        SourcesResolution.apply(project, new Logger(new GithubExtension(), project), sources,
                unusedSourceBuilds(), unusedDownloads(), new HashSet<File>());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> ((ProjectInternal) project).evaluate());
        assertTrue(collectMessages(thrown).contains("url"));
    }

    private static String collectMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append('\n');
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return sb.toString();
    }

    private static SourceBuilds stubSourceBuilds(File jar, String expectedCloneUrl, String expectedRef) {
        return new SourceBuilds() {
            public File buildFromSource(String owner, String repo, String branch, String commitSha) {
                throw new UnsupportedOperationException();
            }
            public File buildFromGit(String cloneUrl, String ref) {
                assertEquals(expectedCloneUrl, cloneUrl);
                assertEquals(expectedRef, ref);
                return jar;
            }
        };
    }

    private static SourceBuilds unusedSourceBuilds() {
        return new SourceBuilds() {
            public File buildFromSource(String owner, String repo, String branch, String commitSha) {
                throw new UnsupportedOperationException();
            }
            public File buildFromGit(String cloneUrl, String ref) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static Downloads unusedDownloads() {
        return (jarUrl, headers, sha256) -> {
            throw new UnsupportedOperationException();
        };
    }

    private static OkHttpClient clientReturningCannedJar(byte[] body) {
        return new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        return new Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(ResponseBody.create(body, MediaType.parse("application/octet-stream")))
                                .build();
                    }
                })
                .build();
    }

    private static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
