package io.github.intisy.gradle.github.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.plugin.Logger;
import io.github.intisy.gradle.github.api.config.ResourcesExtension;
import io.github.intisy.gradle.github.impl.github.GitHub;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestReleaseAssets {
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
    public void testSelectJarAssetEmptyArray() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.0");
        assertNull(result, "Should return null when the asset array is empty");
    }

    @Test
    public void testSelectJarAssetMissingNamePropertyThrows() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        JsonObject noName = new JsonObject();
        noName.addProperty("browser_download_url", "https://example.com/mystery.jar");
        assets.add(noName);
        assertThrows(NullPointerException.class, () -> gh.selectJarAsset(assets, "my-lib", "1.0"));
    }

    @Test
    public void testSelectJarAssetStandaloneMatch() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("my-lib-sources.jar"));
        assets.add(asset("my-lib-standalone.jar"));
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "3.0");
        assertNotNull(result);
        assertEquals("my-lib-standalone.jar", result.get("name").getAsString());
    }

    @Test
    public void testSelectJarAssetClassifierNeitherSourcesNorJavadocBecomesFallback() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("my-lib-api.jar"));
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.0");
        assertNotNull(result);
        assertEquals("my-lib-api.jar", result.get("name").getAsString());
    }

    @Test
    public void testSelectJarAssetTwoExactMatchesReturnsFirst() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        JsonObject first = asset("my-lib.jar");
        first.addProperty("marker", "first");
        JsonObject second = asset("my-lib.jar");
        second.addProperty("marker", "second");
        assets.add(first);
        assets.add(second);
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.0");
        assertNotNull(result);
        assertEquals("first", result.get("marker").getAsString());
    }

    @Test
    public void testSelectJarAssetCaseDifferenceFailsExactMatch() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("My-Lib.jar"));
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.0");
        assertNotNull(result);
        assertEquals("My-Lib.jar", result.get("name").getAsString(), "Case-sensitive equals() misses the exact match, so it is picked up only as the generic fallback");
    }

    @Test
    public void testSelectJarAssetVersionWithRegexSignificantCharacters() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("my-lib-1.0.0+build.1.jar"));
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.0.0+build.1");
        assertNotNull(result);
        assertEquals("my-lib-1.0.0+build.1.jar", result.get("name").getAsString());
    }

    @Test
    public void testSelectJarAssetArrayOrderOverridesDocumentedPriority() {
        GitHub gh = makeGitHub();
        JsonArray assets = new JsonArray();
        assets.add(asset("my-lib-1.0.jar"));
        assets.add(asset("my-lib.jar"));
        JsonObject result = gh.selectJarAsset(assets, "my-lib", "1.0");
        assertNotNull(result);
        assertEquals("my-lib-1.0.jar", result.get("name").getAsString(), "The loop returns on the first exact/versioned/standalone hit it meets while scanning, so a versioned match earlier in the array wins over an exact match later in the array, even though the javadoc lists exact match as priority (1)");
    }
}
