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
     * A character {@link java.net.URI} rejects inside userinfo (a trailing newline, a space, a
     * brace) makes {@code new URI(url)} throw, so a fallback that gives up on an exception and
     * returns the credential verbatim would leak it. A trailing newline is the ordinary shape of
     * {@code file("token.txt").text} in Groovy.
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

    /**
     * A userinfo character that terminates {@link java.net.URI}'s authority early ({@code /},
     * {@code ?}, {@code #}) makes {@code URI} parse the URL "successfully", but assigns everything
     * from that character onward to the path or query instead of the authority, so a strip that
     * only inspects the parsed authority never sees the rest of the credential and returns it
     * verbatim. A base64-shaped token (the ordinary shape of an Azure DevOps PAT) routinely
     * contains {@code /} and {@code +}; a tab is included alongside the already-covered newline,
     * space, and brace as another character a hand-typed or file-read token can carry.
     */
    @Test
    public void everyUnusualUserinfoCharacterIsStrippedNotJustTheOnesUriRejects() {
        for (String special : SPECIAL_USERINFO_CHARACTERS) {
            String url = "https://user:SECRET" + special + "TOKEN@host.example/a.jar";
            String redacted = UrlRedaction.redact(url);
            assertFalse(redacted.contains("SECRET"), "leaked credential for '" + describe(special) + "': " + redacted);
            assertFalse(redacted.contains("user:"), "leaked username for '" + describe(special) + "': " + redacted);
        }
    }

    static final String[] SPECIAL_USERINFO_CHARACTERS = {"/", "?", "#", "+", " ", "\n", "{", "\t"};

    static String describe(String special) {
        if (special.equals("\n")) {
            return "newline";
        }
        if (special.equals("\t")) {
            return "tab";
        }
        if (special.equals(" ")) {
            return "space";
        }
        return special;
    }
}
