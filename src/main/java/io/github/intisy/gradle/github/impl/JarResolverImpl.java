package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.capability.Downloads;
import io.github.intisy.gradle.github.api.capability.JarResolver;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.api.model.ResolutionRequest;

import java.io.File;
import java.io.IOException;

/**
 * Dispatches a {@link ResolutionRequest} to {@link Releases}, {@link SourceBuilds}, or
 * {@link Downloads} by its strategy.
 */
public final class JarResolverImpl implements JarResolver {
    private final Releases releases;
    private final SourceBuilds sourceBuilds;
    private final Downloads downloads;

    /**
     * @param releases the capability to dispatch a release-strategy request to.
     * @param sourceBuilds the capability to dispatch a source- or git-strategy request to.
     * @param downloads the capability to dispatch a url-strategy request to.
     */
    public JarResolverImpl(Releases releases, SourceBuilds sourceBuilds, Downloads downloads) {
        this.releases = releases;
        this.sourceBuilds = sourceBuilds;
        this.downloads = downloads;
    }

    /**
     * @param request the coordinate to resolve.
     * @return the resolved jar file; never null.
     * @throws IOException if {@code request} names a source or git build and the underlying
     * {@link SourceBuilds#buildFromSource} call fails, or names a url download and the underlying
     * {@link Downloads#download} call fails.
     */
    @Override
    public File resolve(ResolutionRequest request) throws IOException {
        switch (request.getStrategy()) {
            case SOURCE:
                return sourceBuilds.buildFromSource(request.getOwner(), request.getRepo(), request.getBranch(), request.getCommitSha());
            case GIT:
                return sourceBuilds.buildFromSource(request.getCloneUrl(), request.getRef());
            case URL:
                return downloads.download(request.getJarUrl(), request.getHeaders(), request.getSha256());
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
