package io.github.intisy.gradle.github.impl.github;

import io.github.intisy.gradle.github.api.config.ResourceSettings;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The token precedence, one case per step: {@code auth.token}, {@code auth.tokenFile}, the
 * deprecated {@code accessToken}, {@code GITHUB_TOKEN}, {@code GH_TOKEN}, then {@code gh auth
 * token}.
 */
public class TestTokenResolution {

    @Test
    public void anExplicitTokenWinsOverEverythingElse() {
        GithubExtension extension = new GithubExtension();
        extension.getAuth().setToken("from-auth-token");
        Map<String, String> environment = environmentWith("GITHUB_TOKEN", "from-github-token");

        assertEquals("from-auth-token", makeGitHub(extension, environment, "from-cli").getApiKey());
    }

    @Test
    public void aTokenFileWinsOverTheEnvironment(@TempDir File directory) throws IOException {
        File tokenFile = new File(directory, "token.txt");
        Files.write(tokenFile.toPath(), "from-token-file\n".getBytes(StandardCharsets.UTF_8));
        GithubExtension extension = new GithubExtension();
        extension.getAuth().setTokenFile(tokenFile);
        Map<String, String> environment = environmentWith("GITHUB_TOKEN", "from-github-token");

        assertEquals("from-token-file", makeGitHub(extension, environment, "from-cli").getApiKey());
    }

    @SuppressWarnings("deprecation") // the deprecated accessToken keeps its place in the precedence
    @Test
    public void theDeprecatedAccessTokenWinsOverTheEnvironment() {
        GithubExtension extension = new GithubExtension();
        extension.setAccessToken("from-access-token");
        Map<String, String> environment = environmentWith("GITHUB_TOKEN", "from-github-token");

        assertEquals("from-access-token", makeGitHub(extension, environment, "from-cli").getApiKey());
    }

    @Test
    public void githubTokenIsReadWhenNothingIsConfigured() {
        Map<String, String> environment = environmentWith("GITHUB_TOKEN", "from-github-token");

        assertEquals("from-github-token", makeGitHub(new GithubExtension(), environment, "from-cli").getApiKey());
    }

    @Test
    public void ghTokenIsReadWhenGithubTokenIsAbsent() {
        Map<String, String> environment = environmentWith("GH_TOKEN", "from-gh-token");

        assertEquals("from-gh-token", makeGitHub(new GithubExtension(), environment, "from-cli").getApiKey());
    }

    @Test
    public void githubTokenWinsOverGhToken() {
        Map<String, String> environment = environmentWith("GITHUB_TOKEN", "from-github-token");
        environment.put("GH_TOKEN", "from-gh-token");

        assertEquals("from-github-token", makeGitHub(new GithubExtension(), environment, "from-cli").getApiKey());
    }

    /**
     * An environment variable set to the empty string is how a CI runner spells "not provided", and
     * taking it as a token sends an empty Authorization header rather than falling through.
     */
    @Test
    public void anEmptyEnvironmentVariableIsNotAToken() {
        Map<String, String> environment = environmentWith("GITHUB_TOKEN", "   ");

        assertEquals("from-cli", makeGitHub(new GithubExtension(), environment, "from-cli").getApiKey());
    }

    @Test
    public void theCliTokenIsTheLastResort() {
        assertEquals("from-cli", makeGitHub(new GithubExtension(), noEnvironment(), "from-cli").getApiKey());
    }

    @Test
    public void nothingConfiguredAndNoCliResolvesToNull() {
        assertNull(makeGitHub(new GithubExtension(), noEnvironment(), null).getApiKey());
    }

    /**
     * Reaching the CLI spawns a process, so a null answer has to be cached like any other. Without
     * that, an unauthenticated build spawns {@code gh} once per API request.
     */
    @Test
    public void anUnresolvedTokenIsAskedForOnlyOnce() {
        CountingCli cli = new CountingCli(null);
        GitHub gitHub = new GitHub(new SilentLogger(), new ResourceSettings(), new GithubExtension(),
                noEnvironment()::get, cli, new OkHttpClient());

        gitHub.getApiKey();
        gitHub.getApiKey();
        gitHub.getApiKey();

        assertEquals(1, cli.calls, "the CLI must be asked once, not once per caller");
    }

    private GitHub makeGitHub(GithubExtension extension, Map<String, String> environment, String cliToken) {
        return new GitHub(new SilentLogger(), new ResourceSettings(), extension,
                environment::get, new CountingCli(cliToken), new OkHttpClient());
    }

    private Map<String, String> environmentWith(String name, String value) {
        Map<String, String> environment = new HashMap<String, String>();
        environment.put(name, value);
        return environment;
    }

    private Map<String, String> noEnvironment() {
        return new HashMap<String, String>();
    }

    /** Answers a fixed token without running {@code gh}, and counts how often it was asked. */
    private static final class CountingCli extends GitHubCli {
        private final String token;
        int calls;

        CountingCli(String token) {
            super(new SilentLogger());
            this.token = token;
        }

        @Override
        public String authToken() {
            calls++;
            return token;
        }
    }

    private static final class SilentLogger implements GitHubLogger {
        @Override
        public void log(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void warn(String message) {
        }
    }
}
