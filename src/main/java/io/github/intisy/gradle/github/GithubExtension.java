package io.github.intisy.gradle.github;

import java.util.List;

public class GithubExtension {
    private String accessToken;

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}