package io.github.intisy.gradle.github.api.model;

import io.github.intisy.gradle.github.api.capability.Publishing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A GitHub release: its identity ({@code id}, {@code tag}, {@code name}, {@code htmlUrl}), the URL
 * assets are uploaded to, and the assets it already carries.
 */
public final class Release {
    private final String id;
    private final String tag;
    private final String name;
    private final String htmlUrl;
    private final String uploadUrl;
    private final List<ReleaseAsset> assets;

    /**
     * @param id the release's GitHub-assigned identifier.
     * @param tag the git tag the release was created for.
     * @param name the release's human-readable title, or null if none was set.
     * @param htmlUrl the release's web page URL.
     * @param uploadUrl the URL asset uploads are posted to (a URI template; callers should use
     * {@link Publishing#uploadAsset} rather than build requests against it directly).
     * @param assets the assets already attached to the release; null is read as none.
     */
    public Release(String id, String tag, String name, String htmlUrl, String uploadUrl, List<ReleaseAsset> assets) {
        this.id = id;
        this.tag = tag;
        this.name = name;
        this.htmlUrl = htmlUrl;
        this.uploadUrl = uploadUrl;
        this.assets = assets == null
                ? Collections.<ReleaseAsset>emptyList()
                : Collections.unmodifiableList(new ArrayList<ReleaseAsset>(assets));
    }

    /**
     * @return the release's GitHub-assigned identifier.
     */
    public String getId() {
        return id;
    }

    /**
     * @return the git tag the release was created for.
     */
    public String getTag() {
        return tag;
    }

    /**
     * @return the release's human-readable title, or null if none was set.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the release's web page URL.
     */
    public String getHtmlUrl() {
        return htmlUrl;
    }

    /**
     * @return the URL asset uploads are posted to.
     */
    public String getUploadUrl() {
        return uploadUrl;
    }

    /**
     * @return the assets already attached to the release, empty when it carries none.
     */
    public List<ReleaseAsset> getAssets() {
        return assets;
    }

    /**
     * @param assetName the asset name to look for.
     * @return the attached asset with that name, or null if the release carries none.
     */
    public ReleaseAsset assetNamed(String assetName) {
        for (ReleaseAsset asset : assets) {
            if (asset.getName().equals(assetName)) {
                return asset;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Release)) {
            return false;
        }
        Release that = (Release) o;
        return Objects.equals(id, that.id) && Objects.equals(tag, that.tag) && Objects.equals(name, that.name)
                && Objects.equals(htmlUrl, that.htmlUrl) && Objects.equals(uploadUrl, that.uploadUrl)
                && Objects.equals(assets, that.assets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tag, name, htmlUrl, uploadUrl, assets);
    }

    @Override
    public String toString() {
        return "Release{id='" + id + "', tag='" + tag + "', name='" + name + "', htmlUrl='" + htmlUrl
                + "', uploadUrl='" + uploadUrl + "', assets=" + assets + "}";
    }
}
