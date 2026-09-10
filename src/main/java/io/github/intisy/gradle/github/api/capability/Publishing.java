package io.github.intisy.gradle.github.api.capability;

import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.api.model.Release;

import java.io.File;
import java.io.IOException;

/**
 * Creates GitHub releases and uploads assets to them.
 */
public interface Publishing {
    /**
     * Creates a release for {@code tag}, or returns the existing release if one already exists
     * for that tag.
     *
     * @param owner the GitHub account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     * @param tag the git tag for the release (GitHub auto-creates a lightweight tag if absent).
     * @param name the human-readable release title; if null, defaults to {@code tag}.
     * @return the created or pre-existing release, including its asset upload URL.
     * @throws RuntimeException if authentication fails or the GitHub API returns an error.
     * @throws RateLimitException if the GitHub API rate limit has been exceeded.
     */
    Release ensureRelease(String owner, String repo, String tag, String name);

    /**
     * Uploads a file as an asset attached to {@code release}, replacing an asset of the same name
     * if the release already carries one.
     *
     * @param release the release to attach the asset to, as returned by {@link #ensureRelease}.
     * @param file the file to upload.
     * @param assetName the asset name as it will appear in the release.
     * @throws IOException if the upload request fails, or if an existing asset of that name could
     * not be removed first.
     * @apiNote The replacement is not a convenience. GitHub refuses an upload whose name is already
     * taken, and {@link #ensureRelease} hands back an existing release rather than failing, so
     * without it publishing one version twice succeeds at the release and then fails at every
     * asset. A rolling version, such as a snapshot republished on each push, is nothing but that
     * case.
     */
    void uploadAsset(Release release, File file, String assetName) throws IOException;
}
