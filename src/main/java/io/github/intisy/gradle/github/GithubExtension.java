package io.github.intisy.gradle.github;

import java.io.File;
import java.nio.file.Path;

@SuppressWarnings("unused")
public class GithubExtension {
    private String accessToken;
    private boolean debug;

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setAccessToken(Path accessToken) {
        this.accessToken = accessToken.toString();
    }

    public void setAccessToken(File accessToken) {
        this.accessToken = accessToken.toString();
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}