package io.github.intisy.gradle.github.utils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Strips the parts of a URL that can carry a credential (userinfo, the query string) before it
 * is used in a log line or an exception message.
 *
 * @implNote A presigned or {@code ?token=}-style URL is the ordinary shape for a private Nexus,
 * S3, or Artifactory download, and {@code https://oauth2:TOKEN@host/repo.git} is the ordinary
 * shape for a private git clone URL; both carry a credential in a place this method removes.
 * Locating that credential is done textually, not via {@link URI}: a credential that contains a
 * {@code /} or {@code +} (the standard base64 alphabet an Azure DevOps PAT or similar token is
 * drawn from), or a character {@link URI} rejects outright (a raw newline, a space, a brace, the
 * ordinary shape of a token read via Groovy's {@code file("token.txt").text}), makes {@code URI}
 * either throw or silently mis-parse the authority. So {@link #redact} checks first, on the raw
 * text, whether the shape between {@code "://"} and the last {@code '@'} looks like {@code
 * userinfo:secret@}, and if so removes that whole span outright before ever asking {@link URI} to
 * parse anything.
 * <p>That check is scoped to end at the first {@code ?} or {@code #} after {@code "://"} (never at
 * a {@code /}, since a leaked credential's own {@code /} must still be searched past). Without that
 * bound, a credential-free URL whose query or fragment happens to contain a colon and a later
 * {@code @} (an ordinary shape: a {@code mailto:} link, a {@code notify=admin@example.com}
 * parameter) would have its host and path destroyed by a match that was never really userinfo at
 * all; RFC 3986 never allows a raw, unencoded {@code ?} or {@code #} inside userinfo, so bounding
 * the search there loses no real coverage. Only a URL with no {@code userinfo:secret@} span falls
 * through to structured {@link URI} parsing (needed to preserve a port cleanly), and only a URL
 * that {@link URI} still cannot parse falls through further to a best-effort manual strip.
 */
public final class UrlRedaction {
    private UrlRedaction() {
    }

    /**
     * @param url the URL to redact; may be {@code null}.
     * @return {@code url} with any userinfo and query string removed, or {@code null} if {@code
     * url} was {@code null}. A value this method cannot parse as a URI (for example, the {@code
     * git@host:owner/repo.git} scp-like syntax, or a URI-illegal character inside userinfo) has
     * its userinfo and query string stripped on a best-effort textual basis instead.
     */
    public static String redact(String url) {
        if (url == null) {
            return null;
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return stripQuery(url);
        }
        int authorityStart = schemeEnd + 3;
        int searchEnd = indexOfQueryOrFragment(url, authorityStart);
        int lastAt = url.lastIndexOf('@', searchEnd - 1);
        if (lastAt > authorityStart && lastAt < searchEnd && url.substring(authorityStart, lastAt).indexOf(':') >= 0) {
            String withoutUserinfo = url.substring(0, authorityStart) + url.substring(lastAt + 1);
            return stripQuery(withoutUserinfo);
        }
        try {
            URI uri = new URI(url);
            StringBuilder result = new StringBuilder();
            if (uri.getScheme() != null) {
                result.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                result.append(uri.getHost());
                if (uri.getPort() != -1) {
                    result.append(':').append(uri.getPort());
                }
            } else if (uri.getAuthority() != null) {
                result.append(stripUserinfo(uri.getAuthority()));
            }
            if (uri.getPath() != null) {
                result.append(uri.getPath());
            }
            return result.length() > 0 ? result.toString() : stripUserinfoAndQueryManually(url);
        } catch (URISyntaxException e) {
            return stripUserinfoAndQueryManually(url);
        }
    }

    /**
     * @return the index of the first {@code ?} or {@code #} at or after {@code from}, or {@code
     * url.length()} if neither appears. Deliberately not bounded at {@code /}: a leaked
     * credential's own {@code /} (an Azure DevOps PAT's base64 alphabet) must still be searched
     * past to find the real {@code @} that separates it from the host.
     */
    private static int indexOfQueryOrFragment(String url, int from) {
        int question = url.indexOf('?', from);
        int hash = url.indexOf('#', from);
        int end = url.length();
        if (question >= 0) {
            end = Math.min(end, question);
        }
        if (hash >= 0) {
            end = Math.min(end, hash);
        }
        return end;
    }

    private static String stripUserinfo(String authority) {
        int at = authority.lastIndexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
    }

    /**
     * @implNote A best-effort textual strip for a value neither {@link #redact}'s own
     * {@code userinfo:secret@} check nor {@link URI} could handle. Only the
     * {@code scheme://authority} shape has a real userinfo slot (an scp-like {@code git@host:...}
     * URL does not: its {@code user@} is a fixed SSH username, never a password), so this only
     * touches the segment between {@code "://"} and the next {@code '/'}.
     */
    private static String stripUserinfoAndQueryManually(String url) {
        String withoutQuery = stripQuery(url);
        int schemeEnd = withoutQuery.indexOf("://");
        if (schemeEnd < 0) {
            return withoutQuery;
        }
        int authorityStart = schemeEnd + 3;
        int pathStart = withoutQuery.indexOf('/', authorityStart);
        String authority = pathStart >= 0 ? withoutQuery.substring(authorityStart, pathStart) : withoutQuery.substring(authorityStart);
        String rest = pathStart >= 0 ? withoutQuery.substring(pathStart) : "";
        return withoutQuery.substring(0, authorityStart) + stripUserinfo(authority) + rest;
    }

    private static String stripQuery(String url) {
        int query = url.indexOf('?');
        return query >= 0 ? url.substring(0, query) : url;
    }
}
