package io.github.intisy.gradle.github.api;

import java.io.File;
import java.io.IOException;

final class JarResolverImpl implements JarResolver {
    private final Releases releases;
    private final SourceBuilds sourceBuilds;

    JarResolverImpl(Releases releases, SourceBuilds sourceBuilds) {
        this.releases = releases;
        this.sourceBuilds = sourceBuilds;
    }

    @Override
    public File resolve(ResolutionRequest request) throws IOException {
        if (request.getStrategy() == ResolutionRequest.Strategy.SOURCE) {
            return sourceBuilds.buildFromSource(request.getOwner(), request.getRepo(), request.getBranch(), request.getCommitSha());
        }
        File jar = releases.downloadJar(request.getOwner(), request.getRepo(), request.getVersion());
        if (jar == null) {
            throw new IOException("No release jar found for " + request.getOwner() + "/" + request.getRepo()
                    + ":" + request.getVersion());
        }
        return jar;
    }
}
