package io.github.intisy.gradle.github;

import java.io.File;
import java.nio.file.Path;

/**
 * Extension for configuring GitHub integration.
 */
@SuppressWarnings("unused")
public class GithubExtension {
    private String accessToken;
    private boolean debug;

    /**
     * @param debug Whether to enable debug logging.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * @return Whether debug logging is enabled.
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * @param accessToken The path to the access token.
     */
    public void setAccessToken(Path accessToken) {
        this.accessToken = accessToken.toString();
    }

    /**
     * @param accessToken The file containing the access token.
     */
    public void setAccessToken(File accessToken) {
        this.accessToken = accessToken.toString();
    }

    /**
     * @param accessToken The access token.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * @return The access token.
     */
    public String getAccessToken() {
        return accessToken;
    }
}