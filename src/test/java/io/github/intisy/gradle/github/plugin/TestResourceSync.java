package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.capability.Repositories;
import io.github.intisy.gradle.github.api.model.RemoteRepo;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.api.config.ResourceSettings;
import io.github.intisy.gradle.github.impl.github.GitHub;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        ResourceSettings resourcesExtension = githubExtension.getResources();
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

    /**
     * {@code https://git.company.com/lib.git} (host/repo, no distinct owner segment, the ordinary
     * shape for a self-hosted git instance dedicated to one team) is a
     * legitimately configured URL, not a misconfiguration; the fail-fast above must not reject it
     * before the verbatim {@link Repositories#cloneOrPullFrom} path is even reached. A fake {@link
     * Repositories} stands in so this stays hermetic: the real clone step is a no-op that never
     * touches the network, and this test cares only about whether the fail-fast let execution get
     * that far.
     */
    @Test
    public void rootLevelRepoUrlPassesTheFailFastAndReachesTheCloneStep() {
        Project project = ProjectBuilder.builder().withName("resource-sync-root-level-test").build();
        project.getPluginManager().apply("java");

        GithubExtension githubExtension = new GithubExtension();
        ResourceSettings resourcesExtension = githubExtension.getResources();
        resourcesExtension.setRepoUrl("https://git.company.com/lib.git");
        Logger logger = new Logger(githubExtension, project);
        NoopRepositories repositories = new NoopRepositories(new RemoteRepo("git.company.com", "lib"));

        ResourceSync.apply(project, logger, resourcesExtension, repositories);

        Task processGitHubResources = project.getTasks().getByName("processGitHubResources");
        try {
            for (Action<? super Task> action : processGitHubResources.getActions()) {
                action.execute(processGitHubResources);
            }
        } catch (RuntimeException ignored) {
            // NoopRepositories never actually populates a checkout, so a later resource-copy step
            // may fail; this test only cares whether the fail-fast check itself let us get past it.
        }
        assertTrue(repositories.cloneOrPullFromCalled,
                "a root-level repo URL must pass the 'is this configured' fail-fast and reach the clone step");
    }

    private static final class NoopRepositories implements Repositories {
        private final RemoteRepo configuredRepo;
        private boolean cloneOrPullFromCalled = false;

        NoopRepositories(RemoteRepo configuredRepo) {
            this.configuredRepo = configuredRepo;
        }

        @Override
        public void cloneOrPull(File target, String owner, String repo, String branch) {
        }

        @Override
        public void cloneOrPullFrom(File target, String cloneUrl, String branch) throws IOException {
            cloneOrPullFromCalled = true;
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Failed to create " + target.getAbsolutePath());
            }
        }

        @Override
        public boolean exists(File path) {
            return false;
        }

        @Override
        public boolean isUpToDate(File path) {
            return false;
        }

        @Override
        public RemoteRepo remoteOf(File projectDir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RemoteRepo configuredRepo() {
            return configuredRepo;
        }
    }
}
