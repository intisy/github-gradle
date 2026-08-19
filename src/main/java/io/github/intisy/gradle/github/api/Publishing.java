package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

/**
 * Creates GitHub releases and uploads assets to them.
 */
public interface Publishing {
    /**
     * Creates a release for {@code tag}, or returns the existing release if one already exists
     * for that tag.
     */
    Release ensureRelease(String owner, String repo, String tag, String name);

    void uploadAsset(Release release, File file, String assetName) throws IOException;
}
