package io.github.intisy.gradle.github.api;

/**
 * The authentication material resolved for GitHub API and git-transport access.
 */
public interface Credentials {
    String apiKey();

    String sshKey();
}
