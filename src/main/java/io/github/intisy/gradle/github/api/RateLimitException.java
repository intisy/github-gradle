package io.github.intisy.gradle.github.api;

/**
 * Thrown when a GitHub API request fails because the rate limit has been exceeded (60
 * requests/hour unauthenticated, 5,000/hour with a token).
 *
 * <p>A caller can catch this specifically and decide how to proceed: retry later, fall back to a
 * previously downloaded result, or surface a clearer error than a generic {@link RuntimeException}
 * would. Supplying a token to the underlying {@link GitHubConfig} raises the limit and is usually
 * the simplest fix.
 */
public class RateLimitException extends RuntimeException {
    /**
     * @param message the detailed, user-facing error message.
     */
    public RateLimitException(String message) {
        super(message);
    }
}
