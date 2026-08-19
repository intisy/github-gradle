package io.github.intisy.gradle.github.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCloneUrlIdentity {

    @Test
    public void ordinaryHttpsUrlDerivesOwnerAndRepoFromTheLastTwoSegments() {
        String[] identity = CloneUrlIdentity.derive("https://gitlab.com/acme/widget.git");
        assertEquals("widget", identity[1]);
        assertTrue(identity[0].startsWith("acme-"), "owner: " + identity[0]);
    }

    /**
     * The exact scenario Critical 2 was raised over: a github.com checkout and a self-hosted
     * checkout sharing the same owner/repo path must never collapse into the same identity,
     * because a directory-existence check has no way to compare a checkout's remote back against
     * the requested URL.
     */
    @Test
    public void twoDistinctHostsWithTheSameOwnerRepoPathDeriveDifferentIdentities() {
        String[] github = CloneUrlIdentity.derive("https://github.com/acme/widget.git");
        String[] gitlabSelfHosted = CloneUrlIdentity.derive("https://gitlab.internal/acme/widget.git");

        assertEquals("widget", github[1]);
        assertEquals("widget", gitlabSelfHosted[1]);
        assertNotEquals(github[0], gitlabSelfHosted[0]);
    }

    @Test
    public void twoDifferentCredentialsOnTheSameHostDeriveDifferentIdentities() {
        String[] first = CloneUrlIdentity.derive("https://oauth2:token-one@gitlab.com/acme/widget.git");
        String[] second = CloneUrlIdentity.derive("https://oauth2:token-two@gitlab.com/acme/widget.git");

        assertNotEquals(first[0], second[0]);
    }

    @Test
    public void userinfoCredentialNeverAppearsInTheDerivedIdentity() {
        String sentinel = "SENTINEL-do-not-leak-9c4e2a";
        String[] identity = CloneUrlIdentity.derive("https://user:" + sentinel + "@gitlab.com/acme/widget.git");

        assertFalse(identity[0].contains(sentinel));
        assertFalse(identity[1].contains(sentinel));
    }

    @Test
    public void queryTokenNeverAppearsInTheDerivedIdentity() {
        String sentinel = "SENTINEL-do-not-leak-7f1b3d";
        String[] identity = CloneUrlIdentity.derive("https://gitlab.com/acme/widget.git?token=" + sentinel);

        assertFalse(identity[0].contains(sentinel));
        assertFalse(identity[1].contains(sentinel));
    }

    /**
     * A root-level repository on a URL that also carries a credential and a port is Important 4's
     * exact scenario: without redaction the derived owner would have been literally
     * {@code user:token@host:port}, and the colons would confuse {@code mkdirs} on Windows.
     */
    @Test
    public void rootLevelRepoWithPortAndCredentialProducesAFilesystemSafeIdentity() {
        String[] identity = CloneUrlIdentity.derive("https://user:secret-token@git.company.com:8443/lib.git");

        assertEquals("lib", identity[1]);
        assertFalse(identity[0].contains(":"));
        assertFalse(identity[0].contains("secret-token"));
        assertFalse(identity[0].contains("user"));
    }

    /**
     * R1's regression test, reopened via the same {@link UrlRedaction} exception-path omission
     * that reopened Important 4: a token containing a newline (the ordinary shape of {@code
     * file("token.txt").text} in Groovy) used to survive verbatim into the derived owner, e.g.
     * {@code "user-ghp_SECRET\n@git.company.com-8443-<hash>"}.
     */
    @Test
    public void newlineInUserinfoNeverAppearsInTheDerivedIdentity() {
        String[] identity = CloneUrlIdentity.derive("https://user:ghp_SECRET\n@git.company.com:8443/lib.git");

        assertEquals("lib", identity[1]);
        assertFalse(identity[0].contains("ghp_SECRET"));
        assertFalse(identity[0].contains("\n"));
        assertFalse(identity[0].contains(":"));
    }

    @Test
    public void spaceInUserinfoNeverAppearsInTheDerivedIdentity() {
        String[] identity = CloneUrlIdentity.derive("https://user:tok en@git.company.com/lib.git");

        assertFalse(identity[0].contains("tok en"));
    }

    @Test
    public void braceInUserinfoNeverAppearsInTheDerivedIdentity() {
        String[] identity = CloneUrlIdentity.derive("https://user:to{k}@git.company.com/lib.git");

        assertFalse(identity[0].contains("to{k}"));
    }
}
