package io.github.intisy.gradle.github.api;

import java.util.Objects;

/**
 * A GitHub-hosted dependency declared by a jar's embedded {@code META-INF/github-dependencies.json}.
 */
public final class DeclaredDependency {
    private final String owner;
    private final String repo;
    private final String version;

    public DeclaredDependency(String owner, String repo, String version) {
        this.owner = owner;
        this.repo = repo;
        this.version = version;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepo() {
        return repo;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeclaredDependency)) {
            return false;
        }
        DeclaredDependency that = (DeclaredDependency) o;
        return Objects.equals(owner, that.owner) && Objects.equals(repo, that.repo) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, repo, version);
    }

    @Override
    public String toString() {
        return "DeclaredDependency{owner='" + owner + "', repo='" + repo + "', version='" + version + "'}";
    }
}
