package io.github.intisy.gradle.github.impl;

/**
 * Thrown when a GitHub API request fails because the rate limit has been exceeded.
 *
 * <p>Callers can catch this specifically to skip the affected operation (see
 * {@code github { skipOnRateLimit = true }}) rather than failing the whole build.
 */
public class RateLimitException extends RuntimeException {
    /**
     * @param message the detailed, user-facing error message.
     */
    public RateLimitException(String message) {
        super(message);
    }
}
