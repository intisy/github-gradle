package io.github.intisy.gradle.github.api;

import java.util.List;

/**
 * Thrown when no release matches the requested tag at all: a typo'd version, a deleted or
 * renamed tag, or a version that was simply never released.
 *
 * <p>This is almost always a caller error, not a normal outcome, which is why
 * {@link io.github.intisy.gradle.github.api.capability.Releases#downloadJar} never absorbs it
 * into an empty {@code Optional} the way it does for {@link ArtifactNotFoundException} (a release
 * that exists but does not carry the requested asset). Confusing the two would make a typo'd
 * version silently vanish from a build instead of failing it.
 */
public class ReleaseNotFoundException extends RuntimeException {
    /** The {@code owner/repo:version} coordinate that could not be found. */
    private final String coordinate;
    /** Every tag variant that was tried and did not resolve to a release. */
    private final List<String> tagsTried;

    /**
     * @param message the detailed, user-facing error message.
     * @param coordinate the {@code owner/repo:version} coordinate that could not be found.
     * @param tagsTried every tag variant that was tried and did not resolve.
     */
    public ReleaseNotFoundException(String message, String coordinate, List<String> tagsTried) {
        super(message);
        this.coordinate = coordinate;
        this.tagsTried = tagsTried;
    }

    /**
     * @return the {@code owner/repo:version} coordinate that could not be found.
     */
    public String getCoordinate() {
        return coordinate;
    }

    /**
     * @return every tag variant that was tried and did not resolve to a release.
     */
    public List<String> getTagsTried() {
        return tagsTried;
    }
}
