package io.github.intisy.gradle.github.api;

/**
 * The authentication material resolved for GitHub API and git-transport access.
 */
public interface Credentials {
    /**
     * @return the resolved API token, or null if none is configured.
     */
    String apiKey();

    /**
     * @return the resolved SSH private key contents, or null if none is configured.
     */
    String sshKey();
}
