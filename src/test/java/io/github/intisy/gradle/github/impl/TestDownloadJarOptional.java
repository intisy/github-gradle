package io.github.intisy.gradle.github.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.api.ArtifactNotFoundException;
import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.api.ReleaseNotFoundException;
import io.github.intisy.gradle.github.api.config.ResourceSettings;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.impl.github.GitHub;
import io.github.intisy.gradle.github.plugin.Logger;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two {@link io.github.intisy.gradle.github.api.capability.Releases#downloadJar}
 * adapters on {@link GitHub}: both are cache-hit tested (no network), and their "nothing
 * matches" and "rate limited" paths are driven through a subclass overriding the public,
 * non-final {@link GitHub#fetchReleaseByTag} seam, which lets these run fully offline.
 *
 * <p>Both overloads now agree on two distinct kinds of absence: an asset missing from a release
 * that does exist ({@link ArtifactNotFoundException}, thrown only at the real not-found sites in
 * {@code getAsset} and caught by both adapters, becoming an empty {@code Optional}) versus a
 * release that does not exist at all ({@link ReleaseNotFoundException}, thrown by
 * {@code fetchReleaseByTag} and caught by neither adapter, so it always propagates). A download or
 * API failure keeps propagating unconverted too. The paired
 * {@code *DownloadFailurePropagatesRatherThanBecomingEmpty} tests mutation-test that a failure is
 * never mistaken for absence: a malformed {@code browser_download_url} makes OkHttp reject the
 * request synchronously (no network involved), and the resulting {@code IllegalArgumentException}
 * must reach the caller rather than being swallowed into {@code Optional.empty()}.
 *
 * <p>{@code user.home} is repointed at {@code @TempDir} for the duration of each test (and
 * restored after) because {@code FileUtils.getGradleHome()} is hard-coded to
 * {@code ~/.gradle/caches} and reads {@code user.home} fresh on every call.
 */
public class TestDownloadJarOptional {

    private GitHub makeGitHub() {
        GithubExtension ext = new GithubExtension();
        ResourceSettings res = new ResourceSettings();
        Logger logger = new Logger(ext);
        return new GitHub(logger, res, ext);
    }

    private void withTempHome(File tempHome, Runnable body) {
        String original = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.getAbsolutePath());
        try {
            body.run();
        } finally {
            System.setProperty("user.home", original);
        }
    }

    private File cachedJarPath(File tempHome, String owner, String fileName) {
        File gradleCaches = new File(new File(tempHome, ".gradle"), "caches");
        File ownerDir = new File(new File(gradleCaches, "github"), owner);
        return new File(ownerDir, fileName);
    }

    private void writeBytes(File file, String content) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes("UTF-8"));
        }
    }

    @Test
    public void downloadJarNoClassifierCacheHitReturnsPresentOptional(@TempDir File tempHome) throws IOException {
        File cached = cachedJarPath(tempHome, "owner", "repo-1.0.0.jar");
        writeBytes(cached, "cached-jar-bytes");

        withTempHome(tempHome, () -> {
            GitHub gh = makeGitHub();
            Optional<File> result = gh.downloadJar("owner", "repo", "1.0.0");
            assertTrue(result.isPresent(), "a cached jar must be returned without needing the network");
            assertEquals(cached.getAbsoluteFile(), result.get().getAbsoluteFile());
        });
    }

    @Test
    public void downloadJarNoClassifierNoMatchingAssetReturnsEmptyOptional(@TempDir File tempHome) {
        JsonObject release = new JsonObject();
        release.add("assets", new JsonArray());

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(makeLogger(), release, null);
            Optional<File> result = gh.downloadJar("owner", "repo", "9.9.9");
            assertFalse(result.isPresent(),
                    "a release with no assets is an absent artifact, reported as an empty Optional "
                            + "via the caught ArtifactNotFoundException, not by throwing");
        });
    }

    @Test
    public void downloadJarNoClassifierNoMatchingReleaseStillThrows(@TempDir File tempHome) {
        ReleaseNotFoundException noRelease = new ReleaseNotFoundException(
                "No release found for owner/repo with tag '9.9.9' or 'v9.9.9'.", "owner/repo:9.9.9",
                Arrays.asList("9.9.9", "v9.9.9"));

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(makeLogger(), null, noRelease);
            assertThrows(ReleaseNotFoundException.class, () -> gh.downloadJar("owner", "repo", "9.9.9"),
                    "a release that does not exist at all is almost always a caller mistake "
                            + "(a typo'd version, a deleted tag), so it must still throw rather than "
                            + "being absorbed into an empty Optional");
        });
    }

    @Test
    public void downloadJarNoClassifierRateLimitedStillThrows(@TempDir File tempHome) {
        RateLimitException rateLimited = new RateLimitException("rate limited");

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(makeLogger(), null, rateLimited);
            assertThrows(RateLimitException.class, () -> gh.downloadJar("owner", "repo", "1.0.0"));
        });
    }

    /**
     * Mutation test: a malformed {@code browser_download_url} makes OkHttp's {@code Request.Builder}
     * reject it synchronously with {@code IllegalArgumentException} (no socket is ever opened), a
     * genuine download failure rather than an absent artifact. It must reach the caller, not be
     * swallowed into an empty {@code Optional} by the {@code ArtifactNotFoundException} catch.
     */
    @Test
    public void downloadJarNoClassifierDownloadFailurePropagatesRatherThanBecomingEmpty(@TempDir File tempHome) {
        JsonObject release = new JsonObject();
        JsonArray assets = new JsonArray();
        JsonObject matching = new JsonObject();
        matching.addProperty("name", "repo.jar");
        matching.addProperty("browser_download_url", "not-a-valid-url");
        assets.add(matching);
        release.add("assets", assets);

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(makeLogger(), release, null);
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> gh.downloadJar("owner", "repo", "1.0.0"));
            assertFalse(thrown instanceof ArtifactNotFoundException,
                    "a malformed download URL is a download failure, not an absent artifact, and "
                            + "must not be caught and turned into Optional.empty()");
        });
    }

    @Test
    public void downloadJarClassifierCacheHitReturnsPresentOptional(@TempDir File tempHome) throws IOException {
        File cached = cachedJarPath(tempHome, "owner", "repo-api-1.0.0.jar");
        writeBytes(cached, "cached-classifier-jar-bytes");

        withTempHome(tempHome, () -> {
            GitHub gh = makeGitHub();
            Optional<File> result = gh.downloadJar("owner", "repo", "1.0.0", "api");
            assertTrue(result.isPresent(), "a cached classifier jar must be returned without needing the network");
            assertEquals(cached.getAbsoluteFile(), result.get().getAbsoluteFile());
        });
    }

    @Test
    public void downloadJarClassifierNoMatchingAssetReturnsEmptyOptional(@TempDir File tempHome) {
        JsonObject release = new JsonObject();
        JsonArray assets = new JsonArray();
        JsonObject unrelated = new JsonObject();
        unrelated.addProperty("name", "repo-other.jar");
        unrelated.addProperty("browser_download_url", "https://example.com/repo-other.jar");
        assets.add(unrelated);
        release.add("assets", assets);

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(makeLogger(), release, null);
            Optional<File> result = gh.downloadJar("owner", "repo", "1.0.0", "api");
            assertFalse(result.isPresent(),
                    "a missing classifier asset is reported as an empty Optional, matching the "
                            + "untouched getAssetWithClassifier's null return");
        });
    }

    @Test
    public void downloadJarClassifierNoMatchingReleaseStillThrows(@TempDir File tempHome) {
        ReleaseNotFoundException noRelease = new ReleaseNotFoundException(
                "No release found for owner/repo with tag '9.9.9' or 'v9.9.9'.", "owner/repo:9.9.9",
                Arrays.asList("9.9.9", "v9.9.9"));

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(makeLogger(), null, noRelease);
            assertThrows(ReleaseNotFoundException.class, () -> gh.downloadJar("owner", "repo", "9.9.9", "api"),
                    "a nonexistent release must still fail loudly for the classifier overload too, "
                            + "the same as the 3-argument overload; only a missing asset within an "
                            + "existing release is reported as an empty Optional");
        });
    }

    @Test
    public void downloadJarClassifierRateLimitedStillThrows(@TempDir File tempHome) {
        RateLimitException rateLimited = new RateLimitException("rate limited");

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(makeLogger(), null, rateLimited);
            assertThrows(RateLimitException.class, () -> gh.downloadJar("owner", "repo", "1.0.0", "api"));
        });
    }

    @Test
    public void downloadJarClassifierDownloadFailurePropagatesRatherThanBecomingEmpty(@TempDir File tempHome) {
        JsonObject release = new JsonObject();
        JsonArray assets = new JsonArray();
        JsonObject matching = new JsonObject();
        matching.addProperty("name", "repo-api.jar");
        matching.addProperty("browser_download_url", "not-a-valid-url");
        assets.add(matching);
        release.add("assets", assets);

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(makeLogger(), release, null);
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> gh.downloadJar("owner", "repo", "1.0.0", "api"));
            assertFalse(thrown instanceof ArtifactNotFoundException,
                    "a malformed download URL is a download failure, not an absent classifier, and "
                            + "must not be caught and turned into Optional.empty()");
        });
    }

    private GitHubLogger makeLogger() {
        return new Logger(new GithubExtension());
    }

    /**
     * Overrides the public, non-final {@link GitHub#fetchReleaseByTag} to return canned data (or
     * throw) instead of making a real HTTP call, so the untouched {@code getAsset}/
     * {@code getAssetWithClassifier} bodies can be driven through their real "nothing matched" and
     * "rate limited" branches offline.
     */
    private static final class FetchStubGitHub extends GitHub {
        private final JsonObject canned;
        private final RuntimeException toThrow;

        FetchStubGitHub(GitHubLogger logger, JsonObject canned, RuntimeException toThrow) {
            super(logger, new ResourceSettings(), new GithubExtension());
            this.canned = canned;
            this.toThrow = toThrow;
        }

        @Override
        public JsonObject fetchReleaseByTag(String repoOwner, String repoName, String version) {
            if (toThrow != null) {
                throw toThrow;
            }
            return canned;
        }
    }
}
