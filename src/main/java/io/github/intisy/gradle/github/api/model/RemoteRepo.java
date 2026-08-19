package io.github.intisy.gradle.github.api.model;

import java.util.Objects;

/**
 * The GitHub owner and repository name a local checkout's {@code origin} remote resolves to.
 */
public final class RemoteRepo {
    private final String owner;
    private final String repo;

    /**
     * @param owner the GitHub account or organization that owns the repository, or null if unresolved.
     * @param repo the repository name, without the owner prefix, or null if unresolved.
     */
    public RemoteRepo(String owner, String repo) {
        this.owner = owner;
        this.repo = repo;
    }

    /**
     * @return the GitHub account or organization that owns the repository, or null if unresolved.
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @return the repository name, without the owner prefix, or null if unresolved.
     */
    public String getRepo() {
        return repo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RemoteRepo)) {
            return false;
        }
        RemoteRepo that = (RemoteRepo) o;
        return Objects.equals(owner, that.owner) && Objects.equals(repo, that.repo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, repo);
    }

    @Override
    public String toString() {
        return "RemoteRepo{owner='" + owner + "', repo='" + repo + "'}";
    }
}
