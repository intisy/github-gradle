package io.github.intisy.gradle.github.api;

/**
 * Thrown when a requested release, or a release asset, genuinely does not exist: no release
 * matches the requested tag, or a matched release has no jar asset satisfying the request.
 *
 * <p>This is distinct from a download or API failure (an I/O error, a non-2xx response, a rate
 * limit): those keep their own exception types and are never represented by this one. A caller
 * that wants "not there" reported as an empty value rather than a thrown exception can catch this
 * specifically, as {@link io.github.intisy.gradle.github.api.capability.Releases#downloadJar}
 * does to produce {@code Optional.empty()}.
 */
public class ArtifactNotFoundException extends RuntimeException {
    /** The {@code owner/repo:version} coordinate that could not be found. */
    private final String coordinate;

    /**
     * @param message the detailed, user-facing error message.
     * @param coordinate the {@code owner/repo:version} coordinate that could not be found.
     */
    public ArtifactNotFoundException(String message, String coordinate) {
        super(message);
        this.coordinate = coordinate;
    }

    /**
     * @return the {@code owner/repo:version} coordinate that could not be found.
     */
    public String getCoordinate() {
        return coordinate;
    }
}
