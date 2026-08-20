package io.github.intisy.gradle.github.utils;

/**
 * Derives an owner/repo-shaped identity from an arbitrary git clone URL, for use as a
 * cache-directory or cached-jar-file naming key only; never used to construct the actual clone
 * URL.
 *
 * @implNote {@code owner} carries a hash of the full URL (the same technique {@code
 * UrlDownloads} uses for its download cache key), so two different hosts, or two different
 * credentials on the same host, that happen to share an owner/repo-shaped path never derive the
 * same identity: a git-existence check on a checkout directory has no way to compare its {@code
 * origin} remote back against the URL that was requested. The URL is redacted before any segment
 * of it becomes part of the identity, so a credential embedded in it (userinfo, a query token)
 * can never land in a directory or file name; each segment is further sanitized against
 * characters a filesystem path cannot contain (a raw {@code host:port} embeds a {@code :}, which
 * also breaks {@code mkdirs} confusingly on Windows).
 */
public final class CloneUrlIdentity {
    private CloneUrlIdentity() {
    }

    /**
     * @param cloneUrl the URL to derive an identity from.
     * @return a two-element array, {@code {owner, repo}}.
     */
    public static String[] derive(String cloneUrl) {
        String redacted = UrlRedaction.redact(cloneUrl);
        String withoutTrailingSlash = redacted.endsWith("/") ? redacted.substring(0, redacted.length() - 1) : redacted;
        String withoutSuffix = withoutTrailingSlash.endsWith(".git")
                ? withoutTrailingSlash.substring(0, withoutTrailingSlash.length() - 4)
                : withoutTrailingSlash;
        String normalized = withoutSuffix.contains("://")
                ? withoutSuffix
                : withoutSuffix.replaceFirst(":", "/");
        String[] segments = normalized.split("/");
        String repo = sanitizeForFileName(segments[segments.length - 1]);
        String ownerSegment = segments.length > 1 ? segments[segments.length - 2] : "unknown";
        String owner = sanitizeForFileName(ownerSegment) + "-" + UrlDigest.sha256Hex(cloneUrl).substring(0, 12);
        return new String[] { owner, repo };
    }

    private static String sanitizeForFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "-");
    }
}
