package io.github.intisy.gradle.github.api.model;

import io.github.intisy.gradle.github.api.capability.Publishing;

import java.util.Objects;

/**
 * A file already attached to a {@link Release}: its {@code name} and the API URL that addresses it.
 *
 * @apiNote The URL is the one GitHub itself reported for the asset, which is also the endpoint a
 * delete goes to. Carrying it means {@link Publishing#uploadAsset} can replace an asset without
 * reassembling an API path, or recovering the owner and repository by parsing them back out of a
 * URL that the release lookup already had in hand.
 */
public final class ReleaseAsset {
    private final String name;
    private final String apiUrl;

    /**
     * @param name the asset name as it appears in the release.
     * @param apiUrl the asset's own API URL, as reported by GitHub.
     */
    public ReleaseAsset(String name, String apiUrl) {
        this.name = name;
        this.apiUrl = apiUrl;
    }

    /**
     * @return the asset name as it appears in the release.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the asset's own API URL, as reported by GitHub.
     */
    public String getApiUrl() {
        return apiUrl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReleaseAsset)) {
            return false;
        }
        ReleaseAsset that = (ReleaseAsset) other;
        return Objects.equals(name, that.name) && Objects.equals(apiUrl, that.apiUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, apiUrl);
    }

    @Override
    public String toString() {
        return "ReleaseAsset{name='" + name + "', apiUrl='" + apiUrl + "'}";
    }
}
