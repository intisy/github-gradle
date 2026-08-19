package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.capability.Downloads;
import io.github.intisy.gradle.github.api.capability.JarResolver;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.api.model.DeclaredDependency;
import io.github.intisy.gradle.github.api.model.Release;
import io.github.intisy.gradle.github.api.model.ResolutionRequest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJarResolver {

    @Test
    public void releaseRequestDelegatesToDownloadJarWithExactArguments() throws IOException {
        File expected = new File("release.jar");
        RecordingReleases releases = new RecordingReleases(expected);
        JarResolver resolver = new JarResolverImpl(releases, new UnusedSourceBuilds(), new UnusedDownloads());

        File resolved = resolver.resolve(ResolutionRequest.fromRelease("owner", "repo", "1.0.0"));

        assertSame(expected, resolved);
        assertEquals("owner", releases.capturedOwner);
        assertEquals("repo", releases.capturedRepo);
        assertEquals("1.0.0", releases.capturedVersion);
    }

    @Test
    public void sourceRequestDelegatesToBuildFromSourceWithExactArguments() throws IOException {
        File expected = new File("source.jar");
        RecordingSourceBuilds sourceBuilds = new RecordingSourceBuilds(expected);
        JarResolver resolver = new JarResolverImpl(new UnusedReleases(), sourceBuilds, new UnusedDownloads());

        File resolved = resolver.resolve(ResolutionRequest.fromSource("owner", "repo", "main", "abc123"));

        assertSame(expected, resolved);
        assertEquals("owner", sourceBuilds.capturedOwner);
        assertEquals("repo", sourceBuilds.capturedRepo);
        assertEquals("main", sourceBuilds.capturedBranch);
        assertEquals("abc123", sourceBuilds.capturedCommitSha);
    }

    @Test
    public void sourceRequestWithNullShaPassesNullThroughRatherThanSubstitutingADefault() throws IOException {
        RecordingSourceBuilds sourceBuilds = new RecordingSourceBuilds(new File("source.jar"));
        JarResolver resolver = new JarResolverImpl(new UnusedReleases(), sourceBuilds, new UnusedDownloads());

        resolver.resolve(ResolutionRequest.fromSource("owner", "repo", "main", null));

        assertNull(sourceBuilds.capturedCommitSha);
    }

    @Test
    public void releaseRequestThrowsNamingTheCoordinateWhenDownloadJarReturnsEmpty() {
        RecordingReleases releases = new RecordingReleases(null);
        JarResolver resolver = new JarResolverImpl(releases, new UnusedSourceBuilds(), new UnusedDownloads());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> resolver.resolve(ResolutionRequest.fromRelease("owner", "repo", "1.0.0")));
        assertTrue(thrown.getMessage().contains("owner"), "message should name the owner");
        assertTrue(thrown.getMessage().contains("repo"), "message should name the repo");
        assertTrue(thrown.getMessage().contains("1.0.0"), "message should name the version");
    }

    @Test
    public void fromReleaseRejectsNullVersion() {
        assertThrows(IllegalArgumentException.class, () -> ResolutionRequest.fromRelease("owner", "repo", null));
    }

    @Test
    public void fromSourceRejectsNullBranch() {
        assertThrows(IllegalArgumentException.class, () -> ResolutionRequest.fromSource("owner", "repo", null, "sha"));
    }

    @Test
    public void gitRequestDelegatesToTheTwoArgBuildFromSourceWithExactArguments() throws IOException {
        File expected = new File("git.jar");
        RecordingSourceBuilds sourceBuilds = new RecordingSourceBuilds(expected);
        JarResolver resolver = new JarResolverImpl(new UnusedReleases(), sourceBuilds, new UnusedDownloads());

        File resolved = resolver.resolve(ResolutionRequest.fromGit("https://gitlab.com/me/lib.git", "v1.0"));

        assertSame(expected, resolved);
        assertEquals("https://gitlab.com/me/lib.git", sourceBuilds.capturedCloneUrl);
        assertEquals("v1.0", sourceBuilds.capturedRef);
        assertNull(sourceBuilds.capturedOwner, "the git strategy must never reach the owner/repo overload");
    }

    @Test
    public void urlRequestDelegatesToDownloadWithExactArguments() throws IOException {
        File expected = new File("url.jar");
        RecordingDownloads downloads = new RecordingDownloads(expected);
        JarResolver resolver = new JarResolverImpl(new UnusedReleases(), new UnusedSourceBuilds(), downloads);
        Map<String, String> headers = Collections.singletonMap("Authorization", "Bearer secret");

        File resolved = resolver.resolve(ResolutionRequest.fromUrl("https://example.com/foo.jar", headers, "deadbeef"));

        assertSame(expected, resolved);
        assertEquals("https://example.com/foo.jar", downloads.capturedJarUrl);
        assertEquals(headers, downloads.capturedHeaders);
        assertEquals("deadbeef", downloads.capturedSha256,
                "a sha256 supplied to fromUrl must reach Downloads.download, not be silently dropped");
    }

    @Test
    public void urlRequestWithoutASha256UsesTheTwoArgOverloadAndPassesNullThrough() throws IOException {
        RecordingDownloads downloads = new RecordingDownloads(new File("url.jar"));
        JarResolver resolver = new JarResolverImpl(new UnusedReleases(), new UnusedSourceBuilds(), downloads);

        resolver.resolve(ResolutionRequest.fromUrl("https://example.com/foo.jar", null));

        assertNull(downloads.capturedSha256);
    }

    @Test
    public void fromGitRejectsNullCloneUrl() {
        assertThrows(IllegalArgumentException.class, () -> ResolutionRequest.fromGit(null, "main"));
    }

    @Test
    public void fromGitAllowsNullRef() {
        ResolutionRequest request = ResolutionRequest.fromGit("https://gitlab.com/me/lib.git", null);
        assertNull(request.getRef());
    }

    @Test
    public void fromUrlRejectsNullJarUrl() {
        assertThrows(IllegalArgumentException.class, () -> ResolutionRequest.fromUrl(null, null));
    }

    @Test
    public void fromUrlWithNullHeadersReturnsAnEmptyMapRatherThanNull() {
        ResolutionRequest request = ResolutionRequest.fromUrl("https://example.com/foo.jar", null);
        assertTrue(request.getHeaders().isEmpty());
    }

    @Test
    public void fromUrlTwoArgOverloadLeavesSha256Null() {
        ResolutionRequest request = ResolutionRequest.fromUrl("https://example.com/foo.jar", null);
        assertNull(request.getSha256());
    }

    @Test
    public void fromUrlThreeArgOverloadCarriesTheSha256() {
        ResolutionRequest request = ResolutionRequest.fromUrl("https://example.com/foo.jar", null, "deadbeef");
        assertEquals("deadbeef", request.getSha256());
    }

    @Test
    public void fromUrlThreeArgOverloadRejectsNullJarUrl() {
        assertThrows(IllegalArgumentException.class, () -> ResolutionRequest.fromUrl(null, null, "deadbeef"));
    }

    private static final class RecordingReleases implements Releases {
        private final File jarToReturn;
        private String capturedOwner;
        private String capturedRepo;
        private String capturedVersion;

        RecordingReleases(File jarToReturn) {
            this.jarToReturn = jarToReturn;
        }

        @Override
        public Optional<File> downloadJar(String owner, String repo, String version) {
            this.capturedOwner = owner;
            this.capturedRepo = repo;
            this.capturedVersion = version;
            return Optional.ofNullable(jarToReturn);
        }

        @Override
        public String latestVersion(String owner, String repo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Release releaseByTag(String owner, String repo, String tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Release latestRelease(String owner, String repo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<File> downloadJar(String owner, String repo, String version, String classifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<File> downloadAllModuleJars(String owner, String repo, String version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<File> resolveWithDependencies(String owner, String repo, String version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DeclaredDependency> declaredDependencies(File jar) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class UnusedReleases implements Releases {
        @Override
        public String latestVersion(String owner, String repo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Release releaseByTag(String owner, String repo, String tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Release latestRelease(String owner, String repo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<File> downloadJar(String owner, String repo, String version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<File> downloadJar(String owner, String repo, String version, String classifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<File> downloadAllModuleJars(String owner, String repo, String version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<File> resolveWithDependencies(String owner, String repo, String version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DeclaredDependency> declaredDependencies(File jar) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingSourceBuilds implements SourceBuilds {
        private final File jarToReturn;
        private String capturedOwner;
        private String capturedRepo;
        private String capturedBranch;
        private String capturedCommitSha;
        private String capturedCloneUrl;
        private String capturedRef;

        RecordingSourceBuilds(File jarToReturn) {
            this.jarToReturn = jarToReturn;
        }

        @Override
        public File buildFromSource(String owner, String repo, String branch, String commitSha) {
            this.capturedOwner = owner;
            this.capturedRepo = repo;
            this.capturedBranch = branch;
            this.capturedCommitSha = commitSha;
            return jarToReturn;
        }

        @Override
        public File buildFromSource(String cloneUrl, String ref) {
            this.capturedCloneUrl = cloneUrl;
            this.capturedRef = ref;
            return jarToReturn;
        }
    }

    private static final class UnusedSourceBuilds implements SourceBuilds {
        @Override
        public File buildFromSource(String owner, String repo, String branch, String commitSha) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File buildFromSource(String cloneUrl, String ref) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingDownloads implements Downloads {
        private final File jarToReturn;
        private String capturedJarUrl;
        private Map<String, String> capturedHeaders;
        private String capturedSha256;

        RecordingDownloads(File jarToReturn) {
            this.jarToReturn = jarToReturn;
        }

        @Override
        public File download(String jarUrl, Map<String, String> headers, String sha256) {
            this.capturedJarUrl = jarUrl;
            this.capturedHeaders = headers;
            this.capturedSha256 = sha256;
            return jarToReturn;
        }
    }

    private static final class UnusedDownloads implements Downloads {
        @Override
        public File download(String jarUrl, Map<String, String> headers, String sha256) {
            throw new UnsupportedOperationException();
        }
    }
}
