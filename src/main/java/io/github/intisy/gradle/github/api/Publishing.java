package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

/**
 * Creates GitHub releases and uploads assets to them.
 *
 * @implNote The brief's illustrative signature named this capability's first method
 * {@code createRelease}, but {@code impl.GitHub} already declares a public
 * {@code createRelease(String, String, String, String)} returning the raw
 * {@code com.google.gson.JsonObject} (kept internal; gson cannot appear in this
 * interface). Java forbids two methods on one class sharing a name and parameter
 * list with different, unrelated return types, so the api-facing method is named
 * {@code publishRelease} instead to avoid that collision without touching the
 * existing method.
 */
public interface Publishing {
    Release publishRelease(String owner, String repo, String tag, String name) throws IOException;

    void uploadAsset(Release release, File file, String assetName) throws IOException;
}
