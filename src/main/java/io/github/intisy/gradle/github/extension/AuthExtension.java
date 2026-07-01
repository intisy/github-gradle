package io.github.intisy.gradle.github.extension;

import java.io.File;
import java.nio.file.Path;

/**
 * Extension for configuring GitHub authentication credentials.
 *
 * <pre>
 * github {
 *     auth {
 *         token     = "ghp_..."                 // a Personal Access Token
 *         tokenFile = file("secrets/github.txt") // a file that contains a token
 *         sshKey    = file("~/.ssh/id_ed25519")  // an SSH private key for git clone/pull
 *     }
 * }
 * </pre>
 *
 * <p>The token (used for REST calls and HTTPS git operations) is resolved with the precedence
 * {@link #getToken() token} &rarr; {@link #getTokenFile() tokenFile}. The {@link #getSshKey() sshKey}
 * is independent and drives git transport over SSH. All fields are optional; when none are set the
 * plugin operates unauthenticated (public repositories only).
 */
@SuppressWarnings("unused")
public class AuthExtension {

    private String token;
    private File tokenFile;
    private File sshKey;

    /**
     * Sets an explicit GitHub token (Personal Access Token) used for REST calls and HTTPS git operations.
     *
     * @param token the token value.
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * @return the explicit token, or null if none was set.
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets a file whose contents are a GitHub token. Used when {@link #getToken() token} is not set.
     *
     * @param tokenFile the file containing the token.
     */
    public void setTokenFile(File tokenFile) {
        this.tokenFile = tokenFile;
    }

    /**
     * Sets a path to a file whose contents are a GitHub token.
     *
     * @param tokenFile the path to the file containing the token.
     */
    public void setTokenFile(Path tokenFile) {
        this.tokenFile = tokenFile.toFile();
    }

    /**
     * @return the token file, or null if none was set.
     */
    public File getTokenFile() {
        return tokenFile;
    }

    /**
     * Sets the SSH private key file used for git clone/pull over SSH.
     *
     * @param sshKey the private key file.
     */
    public void setSshKey(File sshKey) {
        this.sshKey = sshKey;
    }

    /**
     * Sets the path to the SSH private key file used for git clone/pull over SSH.
     *
     * @param sshKey the path to the private key file.
     */
    public void setSshKey(Path sshKey) {
        this.sshKey = sshKey.toFile();
    }

    /**
     * @return the SSH private key file, or null if none was set.
     */
    public File getSshKey() {
        return sshKey;
    }
}
