package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

/**
 * Resolves a {@link ResolutionRequest} to a jar, dispatching to {@link Releases} or
 * {@link SourceBuilds} by the request's own strategy so callers never branch on it themselves.
 */
public interface JarResolver {
    File resolve(ResolutionRequest request) throws IOException;
}
