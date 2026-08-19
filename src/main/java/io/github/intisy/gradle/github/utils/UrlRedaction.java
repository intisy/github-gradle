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
 */
public final class UrlRedaction {
    private UrlRedaction() {
    }

    /**
     * @param url the URL to redact; may be {@code null}.
     * @return {@code url} with any userinfo and query string removed, or {@code null} if {@code
     * url} was {@code null}. A value this method cannot parse as a URI (for example, the {@code
     * git@host:owner/repo.git} scp-like syntax) has its query string stripped on a best-effort
     * basis and is otherwise returned unchanged; that syntax carries no userinfo slot to leak.
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
            return result.length() > 0 ? result.toString() : stripQuery(url);
        } catch (URISyntaxException e) {
            return stripQuery(url);
        }
    }

    private static String stripUserinfo(String authority) {
        int at = authority.lastIndexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
    }

    private static String stripQuery(String url) {
        int query = url.indexOf('?');
        return query >= 0 ? url.substring(0, query) : url;
    }
}
