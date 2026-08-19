package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.api.config.ResourcesExtension;
import io.github.intisy.gradle.github.impl.github.GitHub;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A {@code repoUrl} with a single path segment and no slash parses to a null owner (see
 * {@code GitHub#getResourceRepoOwner}). {@link ResourceSync} must fail fast on that misconfiguration
 * before any network call, rather than passing the null owner into {@code Repositories#cloneOrPull},
 * which would otherwise attempt a real clone against a URL built from the literal string {@code "null"}.
 */
public class TestResourceSync {

    @Test
    public void unparseableRepoUrlFailsFastWithoutTouchingTheNetwork() {
        Project project = ProjectBuilder.builder().withName("resource-sync-test").build();
        project.getPluginManager().apply("java");

        GithubExtension githubExtension = new GithubExtension();
        ResourcesExtension resourcesExtension = githubExtension.getResources();
        resourcesExtension.setRepoUrl("myrepo");

        Logger logger = new Logger(githubExtension, project);
        GitHub gitHub = new GitHub(logger, resourcesExtension, githubExtension);

        ResourceSync.apply(project, logger, resourcesExtension, gitHub);

        Task processGitHubResources = project.getTasks().getByName("processGitHubResources");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            for (Action<? super Task> action : processGitHubResources.getActions()) {
                action.execute(processGitHubResources);
            }
        });
        assertEquals("Variable resourcesExtension.repoUrl is not configured.", thrown.getMessage());
    }
}
