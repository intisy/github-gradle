package io.github.intisy.gradle.github.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestUrlRedaction {

    @Test
    public void stripsUserinfoFromAnHttpsUrl() {
        String redacted = UrlRedaction.redact("https://oauth2:secret-token@gitlab.com/me/lib.git");
        assertEquals("https://gitlab.com/me/lib.git", redacted);
        assertFalse(redacted.contains("secret-token"));
    }

    @Test
    public void stripsAQueryStringToken() {
        String redacted = UrlRedaction.redact("https://nexus.internal/libs/foo-1.0.jar?token=secret-token");
        assertEquals("https://nexus.internal/libs/foo-1.0.jar", redacted);
        assertFalse(redacted.contains("secret-token"));
    }

    @Test
    public void stripsBothUserinfoAndQuery() {
        String redacted = UrlRedaction.redact("https://user:secret-token@host.example/a.jar?x=secret-token");
        assertFalse(redacted.contains("secret-token"));
        assertFalse(redacted.contains("user:"));
    }

    @Test
    public void plainUrlWithNoCredentialIsUnchanged() {
        assertEquals("https://example.com/foo.jar", UrlRedaction.redact("https://example.com/foo.jar"));
    }

    @Test
    public void preservesThePortWhenPresent() {
        assertEquals("https://example.com:8443/foo.jar",
                UrlRedaction.redact("https://user:secret-token@example.com:8443/foo.jar"));
    }

    @Test
    public void nullIsReturnedAsNull() {
        assertNull(UrlRedaction.redact(null));
    }

    @Test
    public void scpLikeSshSyntaxKeepsTheFixedGitUsernameButDropsAnyQuery() {
        String redacted = UrlRedaction.redact("git@gitlab.com:me/lib.git");
        assertEquals("git@gitlab.com:me/lib.git", redacted);
    }
}
