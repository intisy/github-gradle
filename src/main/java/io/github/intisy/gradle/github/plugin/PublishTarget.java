package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.capability.Repositories;
import io.github.intisy.gradle.github.api.model.RemoteRepo;
import io.github.intisy.gradle.github.plugin.extension.PublishExtension;
import org.gradle.api.Project;

/**
 * Answers which GitHub repository a publish targets.
 */
public final class PublishTarget {

    private PublishTarget() {
    }

    /**
     * Resolves the owner and repository a publish targets: whatever the extension states, with the
     * git remote filling in what it does not.
     *
     * @param project the project whose directory the git remote is read from.
     * @param publishExtension the extension supplying explicit overrides.
     * @param repositories the client used to read the git remote.
     * @return the resolved owner and repository.
     * @implNote The remote is read only when at least one half is missing, because reading it is a
     * filesystem walk for a {@code .git} directory and a build that states both needs no answer
     * from git at all.
     */
    public static RemoteRepo resolve(Project project, PublishExtension publishExtension, Repositories repositories) {
        String owner = publishExtension.getOwner();
        String repo = publishExtension.getRepo();
        if (owner != null && repo != null) {
            return new RemoteRepo(owner, repo);
        }
        RemoteRepo remote = repositories.remoteOf(project.getProjectDir());
        return new RemoteRepo(
                owner != null ? owner : remote.getOwner(),
                repo != null ? repo : remote.getRepo());
    }
}
