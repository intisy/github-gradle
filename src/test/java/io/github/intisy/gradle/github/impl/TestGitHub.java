package io.github.intisy.gradle.github.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.api.model.DeclaredDependency;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.plugin.Logger;
import io.github.intisy.gradle.github.api.config.ResourceSettings;
import io.github.intisy.gradle.github.impl.github.GitHub;
import io.github.intisy.gradle.github.utils.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGitHub {
    @Test
    public void testGetRemoteOwnerAndRepoParsesHttpsRemote(@TempDir File projectDir) throws GitAPIException, URISyntaxException {
        try (Git git = Git.init().setDirectory(projectDir).call()) {
            git.remoteAdd()
                    .setName("origin")
                    .setUri(new URIish("https://example.com/SomeOwner/some-repo.git"))
                    .call();
        }
        GitHub gh = makeGitHub();
        String[] result = gh.getRemoteOwnerAndRepo(projectDir);
        assertEquals("SomeOwner", result[0]);
        assertEquals("some-repo", result[1]);
    }

    @Test
    public void testGetRemoteOwnerAndRepoNonGitDirectoryThrows(@TempDir File projectDir) {
        GitHub gh = makeGitHub();
        assertThrows(RuntimeException.class, () -> gh.getRemoteOwnerAndRepo(projectDir));
    }

    @Disabled
    @Test
    public void testAccessToken() throws IOException, GitAPIException {
        GithubExtension githubExtension = new GithubExtension();
        githubExtension.setAccessToken(new File(System.getProperty("user.home") + "/.ssh/id_rsa"));
        githubExtension.setDebug(true);

        ResourceSettings resourcesExtension = new ResourceSettings();
        resourcesExtension.setRepoUrl("https://github.com/Blizzity/libraries");
        resourcesExtension.setBranch("main");

        Logger logger = new Logger(githubExtension);
        GitHub gitHub = new GitHub(logger, resourcesExtension, githubExtension);

        File path = FileUtils.getGradleHome().resolve("resources").resolve(gitHub.getResourceRepoOwner() + "-" + gitHub.getResourceRepoName()).toFile();
        gitHub.cloneOrPullRepository(path, resourcesExtension.getBranch());
    }

    private GitHub makeGitHub() {
        GithubExtension ext = new GithubExtension();
        ResourceSettings res = new ResourceSettings();
        Logger logger = new Logger(ext);
        return new GitHub(logger, res, ext);
    }

    private JsonObject asset(String name) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("browser_download_url", "https://example.com/" + name);
        return obj;
    }

    @Test
    public void testSelectJarAssetExactMatch() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("my-lib-sources.jar"));
        assets.add(asset("other-tool.jar"));      // generic fallback candidate
        assets.add(asset("my-lib.jar"));          // exact match — should win
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.0");
        assertNotNull(result);
        assertEquals("my-lib.jar", result.get("name").getAsString());
    }

    @Test
    public void testSelectJarAssetVersionedFallback() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("my-lib-1.2.3.jar"));    // versioned match
        assets.add(asset("my-lib-sources.jar"));
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.2.3");
        assertNotNull(result);
        assertEquals("my-lib-1.2.3.jar", result.get("name").getAsString());
    }

    @Test
    public void testSelectJarAssetGenericFallback() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("my-lib-sources.jar"));
        assets.add(asset("my-lib-javadoc.jar"));
        assets.add(asset("some-other-artifact.jar")); // generic fallback
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "2.0");
        assertNotNull(result);
        assertEquals("some-other-artifact.jar", result.get("name").getAsString());
    }

    @Test
    public void testSelectJarAssetNoMatch() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("my-lib-sources.jar"));
        assets.add(asset("my-lib-javadoc.jar"));
        assets.add(asset("readme.txt"));
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.0");
        assertNull(result, "Should return null when no usable JAR found");
    }

    @Test
    public void testDeclaredDependenciesCorruptMetadataReturnsEmptyListAndWarns(@TempDir File tempDir) throws IOException {
        File jar = new File(tempDir, "corrupt.jar");
        writeJarWithMetadataEntry(jar, "[{]");
        CapturingLogger logger = new CapturingLogger();
        GitHub gh = makeGitHub(logger);

        List<DeclaredDependency> result = gh.declaredDependencies(jar);

        assertEquals(0, result.size());
        assertEquals(1, logger.warnings.size(), "corrupt metadata should log exactly one warning");
        assertTrue(logger.warnings.get(0).contains("corrupt.jar"), "warning should name the jar");
    }

    @Test
    public void testDeclaredDependenciesNoMetadataEntryDoesNotWarn(@TempDir File tempDir) throws IOException {
        File jar = new File(tempDir, "plain.jar");
        writeJarWithoutMetadataEntry(jar);
        CapturingLogger logger = new CapturingLogger();
        GitHub gh = makeGitHub(logger);

        List<DeclaredDependency> result = gh.declaredDependencies(jar);

        assertEquals(0, result.size());
        assertEquals(0, logger.warnings.size(), "missing metadata is not corruption and must not warn");
    }

    private GitHub makeGitHub(GitHubLogger logger) {
        GithubExtension ext = new GithubExtension();
        ResourceSettings res = new ResourceSettings();
        return new GitHub(logger, res, ext);
    }

    private void writeJarWithMetadataEntry(File jar, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("META-INF/github-dependencies.json"));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private void writeJarWithoutMetadataEntry(File jar) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static final class CapturingLogger implements GitHubLogger {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void log(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    }
}
