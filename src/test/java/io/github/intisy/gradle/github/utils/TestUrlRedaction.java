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

    /**
     * R1's regression tests. A character {@link java.net.URI} rejects inside userinfo (a trailing
     * newline, a space, a brace) makes {@code new URI(url)} throw, and the exception-path fallback
     * used to skip {@code stripUserinfo} entirely, returning the credential verbatim: the exact
     * shape Critical 1 fixed for headers, reopened here for the URL itself. A trailing newline is
     * the ordinary shape of {@code file("token.txt").text} in Groovy.
     */
    @Test
    public void newlineInUserinfoIsStillStripped() {
        String redacted = UrlRedaction.redact("https://oauth2:ghp_abc\n@github.com/o/r.git");
        assertEquals("https://github.com/o/r.git", redacted);
        assertFalse(redacted.contains("ghp_abc"));
        assertFalse(redacted.contains("oauth2"));
    }

    @Test
    public void spaceInUserinfoIsStillStripped() {
        String redacted = UrlRedaction.redact("https://user:tok en@host/a.jar");
        assertEquals("https://host/a.jar", redacted);
        assertFalse(redacted.contains("tok en"));
        assertFalse(redacted.contains("user"));
    }

    @Test
    public void braceInUserinfoIsStillStripped() {
        String redacted = UrlRedaction.redact("https://user:to{k}@host/a.jar");
        assertEquals("https://host/a.jar", redacted);
        assertFalse(redacted.contains("to{k}"));
        assertFalse(redacted.contains("user"));
    }

    @Test
    public void newlineInUserinfoWithAQueryStringStripsBoth() {
        String redacted = UrlRedaction.redact("https://user:tok\nen@host/a.jar?x=also-secret");
        assertEquals("https://host/a.jar", redacted);
        assertFalse(redacted.contains("tok"));
        assertFalse(redacted.contains("also-secret"));
    }
}
