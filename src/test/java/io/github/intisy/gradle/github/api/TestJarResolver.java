package io.github.intisy.gradle.github.api;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
        JarResolver resolver = new JarResolverImpl(releases, new UnusedSourceBuilds());

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
        JarResolver resolver = new JarResolverImpl(new UnusedReleases(), sourceBuilds);

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
        JarResolver resolver = new JarResolverImpl(new UnusedReleases(), sourceBuilds);

        resolver.resolve(ResolutionRequest.fromSource("owner", "repo", "main", null));

        assertNull(sourceBuilds.capturedCommitSha);
    }

    @Test
    public void releaseRequestThrowsIOExceptionNamingTheCoordinateWhenNoAssetIsFound() {
        RecordingReleases releases = new RecordingReleases(null);
        JarResolver resolver = new JarResolverImpl(releases, new UnusedSourceBuilds());

        IOException thrown = assertThrows(IOException.class,
                () -> resolver.resolve(ResolutionRequest.fromRelease("owner", "repo", "1.0.0")));

        assertTrue(thrown.getMessage().contains("owner"));
        assertTrue(thrown.getMessage().contains("repo"));
        assertTrue(thrown.getMessage().contains("1.0.0"));
    }

    @Test
    public void fromReleaseRejectsNullVersion() {
        assertThrows(IllegalArgumentException.class, () -> ResolutionRequest.fromRelease("owner", "repo", null));
    }

    @Test
    public void fromSourceRejectsNullBranch() {
        assertThrows(IllegalArgumentException.class, () -> ResolutionRequest.fromSource("owner", "repo", null, "sha"));
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
        public File downloadJar(String owner, String repo, String version) {
            this.capturedOwner = owner;
            this.capturedRepo = repo;
            this.capturedVersion = version;
            return jarToReturn;
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
        public File downloadJar(String owner, String repo, String version, String classifier) {
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
        public File downloadJar(String owner, String repo, String version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File downloadJar(String owner, String repo, String version, String classifier) {
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
    }

    private static final class UnusedSourceBuilds implements SourceBuilds {
        @Override
        public File buildFromSource(String owner, String repo, String branch, String commitSha) {
            throw new UnsupportedOperationException();
        }
    }
}
