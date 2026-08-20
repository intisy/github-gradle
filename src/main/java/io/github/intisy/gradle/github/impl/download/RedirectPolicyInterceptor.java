package io.github.intisy.gradle.github.impl.download;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Follows a redirect with a policy OkHttp's own on/off {@code followRedirects} cannot express:
 * a same-host redirect keeps the caller's headers (same origin, nothing leaks); a cross-host
 * redirect is still followed, but the caller's headers are stripped first, which is exactly what
 * makes a presigned-URL redirect from a private Nexus/Artifactory/S3-backed host work, since the
 * new URL carries its own authorization; an https-to-http redirect is never followed at all, so a
 * header can never be forwarded to the target in cleartext by way of a downgrade.
 *
 * @implNote Requires the {@link okhttp3.OkHttpClient} this is installed on to have {@code
 * followRedirects(false)} set, so OkHttp's own redirect-following interceptor never intercepts a
 * 3xx before this one gets to decide; this class does its own following by re-invoking {@link
 * Interceptor.Chain#proceed}, the documented pattern for custom redirect handling.
 */
public final class RedirectPolicyInterceptor implements Interceptor {
    private static final int MAX_REDIRECTS = 20;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        for (int redirectCount = 0; ; redirectCount++) {
            Response response = chain.proceed(request);
            if (!isRedirect(response) || redirectCount >= MAX_REDIRECTS) {
                return response;
            }
            String location = response.header("Location");
            HttpUrl target = location == null ? null : response.request().url().resolve(location);
            if (target == null) {
                return response;
            }
            if (isHttpsToHttpDowngrade(request.url(), target)) {
                return response;
            }
            boolean crossHost = isCrossHost(request.url(), target);
            Request.Builder nextBuilder = crossHost
                    ? new Request.Builder().method(request.method(), request.body())
                    : request.newBuilder();
            request = nextBuilder.url(target).build();
            response.close();
        }
    }

    /**
     * @param response the response to inspect.
     * @return true if {@code response} is a redirect status carrying a {@code Location} header.
     */
    public static boolean isRedirect(Response response) {
        int code = response.code();
        return (code == 300 || code == 301 || code == 302 || code == 303 || code == 307 || code == 308)
                && response.header("Location") != null;
    }

    /**
     * @param from the URL being redirected from.
     * @param to the URL being redirected to.
     * @return true if following the redirect from {@code from} to {@code to} would downgrade https to http.
     */
    public static boolean isHttpsToHttpDowngrade(HttpUrl from, HttpUrl to) {
        return from.isHttps() && !to.isHttps();
    }

    /**
     * @param from the URL being redirected from.
     * @param to the URL being redirected to.
     * @return true if {@code to} has a different host (case-insensitively) or a different port
     * than {@code from}. A different port on the same hostname is still a different origin: the
     * caller's headers must not survive a redirect to another port any more than to another host.
     */
    public static boolean isCrossHost(HttpUrl from, HttpUrl to) {
        return !from.host().equalsIgnoreCase(to.host()) || from.port() != to.port();
    }
}
