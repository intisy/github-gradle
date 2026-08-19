package io.github.intisy.gradle.github.api.config;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the behavior of {@link GitHubConfig.Builder}: an untouched builder produces a valid
 * anonymous-access config, every setter is reflected in the built config, and the builder's
 * immutability guarantee holds in both directions.
 */
public class TestGitHubConfigBuilder {

    @Test
    public void buildWithNoCallsProducesAnAnonymousConfig() {
        GitHubConfig config = GitHubConfig.builder().build();

        assertNull(config.getAccessToken());
        assertNull(config.getAuth().getToken());
        assertNull(config.getAuth().getTokenFile());
        assertNull(config.getAuth().getSshKey());
        assertFalse(config.getCli().isEnabled());
        assertTrue(config.getCli().isFallback());
        assertFalse(config.getResilience().isSkipOnRateLimit());
    }

    @Test
    public void everySetterIsReflectedInTheBuiltConfig() {
        File tokenFile = new File("secrets/github.txt");
        File sshKey = new File("id_ed25519");

        GitHubConfig config = GitHubConfig.builder()
                .token("ghp_example")
                .tokenFile(tokenFile)
                .sshKey(sshKey)
                .cliEnabled(true)
                .cliFallback(false)
                .skipOnRateLimit(true)
                .build();

        assertEquals("ghp_example", config.getAuth().getToken());
        assertEquals(tokenFile, config.getAuth().getTokenFile());
        assertEquals(sshKey, config.getAuth().getSshKey());
        assertTrue(config.getCli().isEnabled());
        assertFalse(config.getCli().isFallback());
        assertTrue(config.getResilience().isSkipOnRateLimit());
    }

    @Test
    public void reusingTheBuilderAfterBuildDoesNotAffectTheEarlierConfig() {
        GitHubConfig.Builder builder = GitHubConfig.builder().token("first");
        GitHubConfig first = builder.build();

        builder.token("second").cliEnabled(true).cliFallback(false).skipOnRateLimit(true);
        GitHubConfig second = builder.build();

        assertEquals("first", first.getAuth().getToken());
        assertFalse(first.getCli().isEnabled());
        assertFalse(first.getResilience().isSkipOnRateLimit());

        assertEquals("second", second.getAuth().getToken());
        assertTrue(second.getCli().isEnabled());
        assertTrue(second.getResilience().isSkipOnRateLimit());
    }

    @Test
    public void mutatingASettingsObjectReadFromTheBuiltConfigDoesNotAffectIt() {
        GitHubConfig config = GitHubConfig.builder().token("original").build();

        AuthSettings readBack = config.getAuth();
        readBack.setToken("mutated");

        assertEquals("original", config.getAuth().getToken());
    }
}
