package io.github.intisy.gradle.github;

import java.io.File;
import java.nio.file.Path;
import org.gradle.api.Action;
import groovy.lang.Closure;

/**
 * Extension for configuring GitHub integration.
 */
@SuppressWarnings("unused")
public class GithubExtension {
    private String accessToken;
    private boolean debug;
    private final ResourcesExtension resources = new ResourcesExtension();

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

    /**
     * @return The nested resources extension.
     */
    public ResourcesExtension getResources() {
        return resources;
    }

    /**
     * Configures the nested resources extension using a Gradle action.
     * @param action The configuration action.
     */
    public void resources(Action<? super ResourcesExtension> action) {
        action.execute(resources);
    }

    /**
     * Configures the nested resources extension using a Groovy closure.
     * Supports Gradle Groovy DSL usage: resources { ... }
     * @param closure The configuration closure.
     */
    public void resources(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(resources);
        closure.call(resources);
    }
}