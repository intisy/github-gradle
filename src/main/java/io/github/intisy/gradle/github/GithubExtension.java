package io.github.intisy.gradle.github;

import java.util.List;

public class GithubExtension {
    private String accessToken;
    private boolean debug;

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}