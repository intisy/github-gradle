package io.github.intisy.gradle.github.api.capability;

import io.github.intisy.gradle.github.api.ReleaseNotFoundException;
import io.github.intisy.gradle.github.api.model.ResolutionRequest;

import java.io.File;
import java.io.IOException;

/**
 * Resolves a {@link ResolutionRequest} to a jar, dispatching to {@link Releases},
 * {@link SourceBuilds}, or a jar-download capability by the request's own strategy so callers
 * never branch on it themselves.
 */
public interface JarResolver {
    /**
     * @param request the coordinate to resolve, naming a release version, a GitHub source
     * branch/commit, a git clone URL/ref, or a jar URL; construct via {@link
     * ResolutionRequest#fromRelease}, {@link ResolutionRequest#fromSource}, {@link
     * ResolutionRequest#fromGit}, or {@link ResolutionRequest#fromUrl}.
     * @return the resolved jar file; never null.
     * @throws ReleaseNotFoundException if {@code request} names a release: thrown by this method
     * itself, naming the coordinate, when {@link Releases#downloadJar(String, String, String)}
     * returns an empty {@code Optional} (no release matches the version, or the release has no
     * matching jar asset).
     * @throws IOException if {@code request} names a source or git build and
     * {@link SourceBuilds#buildFromSource} fails to check out or build the requested ref.
     */
    File resolve(ResolutionRequest request) throws IOException;
}
