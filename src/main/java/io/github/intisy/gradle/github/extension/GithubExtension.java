package io.github.intisy.gradle.github.extension;

import java.io.File;
import java.nio.file.Path;
import org.gradle.api.Action;
import groovy.lang.Closure;

/**
 * Extension for configuring GitHub integration.
 *
 * <pre>
 * github {
 *     accessToken = "ghp_..."   // or a file/path containing the token
 *     debug = true
 *
 *     publish {
 *         owner   = "my-org"
 *         repo    = "my-repo"
 *         version = "2.0.0"
 *         jar     = file("build/libs/my-fat.jar")
 *     }
 *
 *     resources {
 *         repoUrl = "https://github.com/my-org/my-resources"
 *         branch  = "main"
 *     }
 * }
 * </pre>
 */
@SuppressWarnings("unused")
public class GithubExtension {
    private final ResourcesExtension resources = new ResourcesExtension();
    private final PublishExtension publish = new PublishExtension();

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
     * @param accessToken The access token string.
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
     * @return The nested publish extension.
     */
    public PublishExtension getPublish() {
        return publish;
    }

    /**
     * Configures the nested publish extension using a Gradle action.
     *
     * @param action The configuration action.
     */
    public void publish(Action<? super PublishExtension> action) {
        action.execute(publish);
    }

    /**
     * Configures the nested publish extension using a Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code publish { ... }}
     *
     * @param closure The configuration closure.
     */
    public void publish(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(publish);
        closure.call(publish);
    }

    /**
     * @return The nested resources extension.
     */
    public ResourcesExtension getResources() {
        return resources;
    }

    /**
     * Configures the nested resources extension using a Gradle action.
     *
     * @param action The configuration action.
     */
    public void resources(Action<? super ResourcesExtension> action) {
        action.execute(resources);
    }

    /**
     * Configures the nested resources extension using a Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code resources { ... }}
     *
     * @param closure The configuration closure.
     */
    public void resources(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(resources);
        closure.call(resources);
    }
}
