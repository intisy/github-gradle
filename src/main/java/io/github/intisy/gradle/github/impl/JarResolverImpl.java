package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.capability.JarResolver;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.api.model.ResolutionRequest;

import java.io.File;
import java.io.IOException;

/**
 * Dispatches a {@link ResolutionRequest} to {@link Releases} or {@link SourceBuilds} by its
 * strategy.
 */
public final class JarResolverImpl implements JarResolver {
    private final Releases releases;
    private final SourceBuilds sourceBuilds;

    /**
     * @param releases the capability to dispatch a release-strategy request to.
     * @param sourceBuilds the capability to dispatch a source-strategy request to.
     */
    public JarResolverImpl(Releases releases, SourceBuilds sourceBuilds) {
        this.releases = releases;
        this.sourceBuilds = sourceBuilds;
    }

    /**
     * @param request the coordinate to resolve.
     * @return the resolved jar file; never null.
     * @throws IOException if {@code request} names a source or git build and the underlying
     * {@link SourceBuilds#buildFromSource} call fails.
     * @throws UnsupportedOperationException if {@code request} names a {@link
     * ResolutionRequest.Strategy#URL} download; not yet wired to a {@code Downloads} capability.
     */
    @Override
    public File resolve(ResolutionRequest request) throws IOException {
        switch (request.getStrategy()) {
            case SOURCE:
                return sourceBuilds.buildFromSource(request.getOwner(), request.getRepo(), request.getBranch(), request.getCommitSha());
            case GIT:
                return sourceBuilds.buildFromSource(request.getCloneUrl(), request.getRef());
            case URL:
                throw new UnsupportedOperationException("URL strategy resolution is not wired yet.");
            case RELEASE:
            default:
                final String owner = request.getOwner();
                final String repo = request.getRepo();
                final String version = request.getVersion();
                return releases.downloadJar(owner, repo, version)
                        .orElseThrow(() -> new RuntimeException("No release jar found for " + owner + ":" + repo + ":" + version));
        }
    }
}
