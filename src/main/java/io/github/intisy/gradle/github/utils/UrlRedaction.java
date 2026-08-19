package io.github.intisy.gradle.github.utils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Strips the parts of a URL that can carry a credential (userinfo, the query string) before it
 * is used in a log line or an exception message.
 *
 * @implNote A presigned or {@code ?token=}-style URL is the ordinary shape for a private Nexus,
 * S3, or Artifactory download, and {@code https://oauth2:TOKEN@host/repo.git} is the ordinary
 * shape for a private git clone URL; both carry a credential in a place this method removes. A
 * credential containing a character {@link URI} rejects (a raw newline, a space, a brace — the
 * ordinary shape of a token read via Groovy's {@code file("token.txt").text}, which keeps a
 * trailing newline) makes {@code new URI(url)} throw rather than parse; the exception path below
 * strips userinfo manually for exactly that reason, rather than giving up and returning the
 * credential verbatim.
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

    private static String stripUserinfo(String authority) {
        int at = authority.lastIndexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
    }

    /**
     * @implNote A best-effort textual strip for a value {@link URI} could not parse. Only the
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
