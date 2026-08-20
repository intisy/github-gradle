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
     * A trailing slash after {@code .git} (a URL copy-pasted with a stray slash) must not defeat
     * the {@code .git} suffix strip: stripping {@code .git} before the trailing slash leaves the
     * suffix in place (it now sits before the slash, not at the string's end), so the derived repo
     * ends up literally {@code "widget.git"} instead of {@code "widget"}, which then fails {@code
     * SourceBuilder#locateBuiltJar}'s {@code name.contains(repo)} check against a jar actually
     * named {@code widget-1.0.jar}.
     */
    @Test
    public void trailingSlashAfterDotGitStillYieldsTheBareRepoName() {
        String[] identity = CloneUrlIdentity.derive("https://gitlab.com/acme/widget.git/");
        assertEquals("widget", identity[1]);
    }

    /**
     * A github.com checkout and a self-hosted checkout sharing the same owner/repo path must
     * never collapse into the same identity, because a directory-existence check has no way to
     * compare a checkout's remote back against the requested URL.
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
     * A root-level repository on a URL that also carries a credential and a port: without
     * redaction and sanitizing, the derived owner would be literally {@code user:token@host:port},
     * and the colons would confuse {@code mkdirs} on Windows.
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
     * A token containing a newline (the ordinary shape of {@code file("token.txt").text} in
     * Groovy) must never survive into the derived owner, e.g. as
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

    /**
     * F1's regression, at the identity level: no userinfo at all, but the query contains a colon
     * and a later {@code @} (an ordinary {@code mailto:} shape). The owner/repo must still derive
     * from the real host and path, not get corrupted by a false-positive credential match.
     */
    @Test
    public void credentialFreeQueryWithAColonAndAnAtSignDoesNotCorruptTheIdentity() {
        String[] identity = CloneUrlIdentity.derive("https://gitlab.com/acme/widget.git?redirect=mailto:a@b.com");

        assertEquals("widget", identity[1]);
        assertTrue(identity[0].startsWith("acme-"), "owner: " + identity[0]);
    }

    /**
     * The identity is what lands on disk, so every character in {@link
     * TestUrlRedaction#SPECIAL_USERINFO_CHARACTERS} that {@link UrlRedaction#redact} must strip
     * has to be proven gone from the derived owner too, not just from the redacted URL string.
     */
    @Test
    public void everyUnusualUserinfoCharacterNeverAppearsInTheDerivedIdentity() {
        for (String special : TestUrlRedaction.SPECIAL_USERINFO_CHARACTERS) {
            String url = "https://user:SECRET" + special + "TOKEN@git.company.com/acme/widget.git";
            String[] identity = CloneUrlIdentity.derive(url);
            assertFalse(identity[0].contains("SECRET"),
                    "owner leaked for '" + TestUrlRedaction.describe(special) + "': " + identity[0]);
            assertFalse(identity[1].contains("SECRET"),
                    "repo leaked for '" + TestUrlRedaction.describe(special) + "': " + identity[1]);
        }
    }
}
