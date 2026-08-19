package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.api.capability.JarResolver;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.api.model.ResolutionRequest;

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
        return releases.downloadJar(request.getOwner(), request.getRepo(), request.getVersion());
    }
}
