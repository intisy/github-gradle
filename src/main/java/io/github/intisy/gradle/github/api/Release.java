package io.github.intisy.gradle.github.api;

import java.util.Objects;

/**
 * A created (or reused) GitHub release, identified by its tag, with the URL assets are uploaded to.
 */
public final class Release {
    private final String tag;
    private final String uploadUrl;

    public Release(String tag, String uploadUrl) {
        this.tag = tag;
        this.uploadUrl = uploadUrl;
    }

    public String getTag() {
        return tag;
    }

    public String getUploadUrl() {
        return uploadUrl;
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
        return Objects.equals(tag, that.tag) && Objects.equals(uploadUrl, that.uploadUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, uploadUrl);
    }

    @Override
    public String toString() {
        return "Release{tag='" + tag + "', uploadUrl='" + uploadUrl + "'}";
    }
}
