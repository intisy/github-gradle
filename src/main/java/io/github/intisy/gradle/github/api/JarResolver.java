package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

/**
 * Resolves a {@link ResolutionRequest} to a jar, dispatching to {@link Releases} or
 * {@link SourceBuilds} by the request's own strategy so callers never branch on it themselves.
 */
public interface JarResolver {
    /**
     * @param request the coordinate to resolve, naming either a release version or a source
     * branch/commit; construct via {@link ResolutionRequest#fromRelease} or {@link ResolutionRequest#fromSource}.
     * @return the resolved jar file; never null.
     * @throws RuntimeException if {@code request} names a release, propagated unchecked from
     * {@link Releases#downloadJar(String, String, String)} when no release matches the version,
     * the release has no matching jar asset, or the download itself fails.
     * @throws IOException if {@code request} names a source build and
     * {@link SourceBuilds#buildFromSource} fails to check out or build the requested commit.
     */
    File resolve(ResolutionRequest request) throws IOException;
}
