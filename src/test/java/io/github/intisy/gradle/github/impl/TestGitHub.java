package io.github.intisy.gradle.github.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.utils.GradleUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestGitHub {
    @Test
    public void testGithub() throws IOException {
//        org.kohsuke.github.GitHub github = org.kohsuke.github.GitHub.connectAnonymously();
//        GitHub.getAsset("SimpleLogger", "Blizzity", "1.12.7", github);
    }

    @Disabled
    @Test
    public void testAccessToken() throws IOException, GitAPIException {
        GithubExtension githubExtension = new GithubExtension();
        githubExtension.setAccessToken(new File(System.getProperty("user.home") + "/.ssh/id_rsa"));
        githubExtension.setDebug(true);

        ResourcesExtension resourcesExtension = new ResourcesExtension();
        resourcesExtension.setRepoUrl("https://github.com/Blizzity/libraries");
        resourcesExtension.setBranch("main");

        Logger logger = new Logger(githubExtension);
        GitHub gitHub = new GitHub(logger, resourcesExtension, githubExtension);

        File path = GradleUtils.getGradleHome().resolve("resources").resolve(gitHub.getResourceRepoOwner() + "-" + gitHub.getResourceRepoName()).toFile();
        gitHub.cloneOrPullRepository(path, resourcesExtension.getBranch());
    }

    private GitHub makeGitHub() {
        GithubExtension ext = new GithubExtension();
        ResourcesExtension res = new ResourcesExtension();
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
}
