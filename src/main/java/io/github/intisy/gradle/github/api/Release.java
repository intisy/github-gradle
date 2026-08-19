package io.github.intisy.gradle.github.api;

import java.util.Objects;

/**
 * A GitHub release: its identity ({@code id}, {@code tag}, {@code name}, {@code htmlUrl}) and the
 * URL assets are uploaded to.
 */
public final class Release {
    private final String id;
    private final String tag;
    private final String name;
    private final String htmlUrl;
    private final String uploadUrl;

    public Release(String id, String tag, String name, String htmlUrl, String uploadUrl) {
        this.id = id;
        this.tag = tag;
        this.name = name;
        this.htmlUrl = htmlUrl;
        this.uploadUrl = uploadUrl;
    }

    public String getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public String getName() {
        return name;
    }

    public String getHtmlUrl() {
        return htmlUrl;
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
        return Objects.equals(id, that.id) && Objects.equals(tag, that.tag) && Objects.equals(name, that.name)
                && Objects.equals(htmlUrl, that.htmlUrl) && Objects.equals(uploadUrl, that.uploadUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tag, name, htmlUrl, uploadUrl);
    }

    @Override
    public String toString() {
        return "Release{id='" + id + "', tag='" + tag + "', name='" + name + "', htmlUrl='" + htmlUrl
                + "', uploadUrl='" + uploadUrl + "'}";
    }
}
