package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.ConsoleGitHubLogger;
import io.github.intisy.gradle.github.api.GitHubLogger;
import io.github.intisy.gradle.github.api.model.RemoteRepo;
import io.github.intisy.gradle.github.api.Repositories;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static io.github.intisy.gradle.github.impl.GitTestFixtures.addCommit;
import static io.github.intisy.gradle.github.impl.GitTestFixtures.cloneLocally;
import static io.github.intisy.gradle.github.impl.GitTestFixtures.createOriginRepo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link RepositoriesImpl} through {@link Repositories}, the way a library consumer
 * would, so a regression that only shows up once the single {@link GitHub} a {@code GitHubApi} was
 * built with is reused across independent calls is caught here.
 */
public class TestRepositoriesImpl {
    private static final GitHubLogger LOGGER = new ConsoleGitHubLogger(false);

    private Repositories repositoriesConfiguredFor(String repoUrl) {
        ResourcesExtension configured = new ResourcesExtension();
        configured.setRepoUrl(repoUrl);
        GitHub gitHub = new GitHub(LOGGER, configured, new GithubExtension());
        return new RepositoriesImpl(gitHub, new GithubExtension(), LOGGER);
    }

    private Repositories repositoriesWithNoConfiguredRepo() {
        return repositoriesConfiguredFor(null);
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void cloneOrPullSucceedsTwiceAgainstAnUnconfiguredResourcesExtension(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File target = new File(tempDir, "clone");
        cloneLocally(origin, target);

        Repositories repositories = repositoriesWithNoConfiguredRepo();

        assertDoesNotThrow(() -> repositories.cloneOrPull(target, "acme", "widget", null));
        assertDoesNotThrow(() -> repositories.cloneOrPull(target, "acme", "widget", null));

        assertEquals("initial content", readFile(new File(target, "file.txt")));
    }

    @Test
    public void cloneOrPullForADifferentRepoThanConfiguredUsesTheArgumentsAndLeavesConfiguredRepoIntact(@TempDir File tempDir) throws IOException, GitAPIException {
        File configuredOrigin = createOriginRepo(new File(tempDir, "configured-origin"));
        File otherOrigin = createOriginRepo(new File(tempDir, "other-origin"));
        File target = new File(tempDir, "clone");
        cloneLocally(otherOrigin, target);
        addCommit(otherOrigin, "second.txt", "second content");

        Repositories repositories = repositoriesConfiguredFor(configuredOrigin.toURI().toString());

        assertDoesNotThrow(() -> repositories.cloneOrPull(target, "other-owner", "other-repo", null));

        assertEquals("second content", readFile(new File(target, "second.txt")));

        RemoteRepo configuredRepo = repositories.configuredRepo();
        assertEquals("configured-origin", configuredRepo.getRepo());
    }

    @Test
    public void isUpToDateDerivesTheRepoFromTheCheckoutInsteadOfAnUnconfiguredExtension(@TempDir File tempDir) throws IOException, GitAPIException {
        File origin = createOriginRepo(new File(tempDir, "origin"));
        File clone = new File(tempDir, "clone");
        cloneLocally(origin, clone);

        Repositories repositories = repositoriesWithNoConfiguredRepo();

        assertTrue(repositories.isUpToDate(clone));
    }
}
