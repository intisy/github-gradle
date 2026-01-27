package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.utils.GradleUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

public class TestGitHub {
    @Test
    public void testGithub() throws IOException {
//        org.kohsuke.github.GitHub github = org.kohsuke.github.GitHub.connectAnonymously();
//        GitHub.getAsset("SimpleLogger", "Blizzity", "1.12.7", github);
    }

    @Test
    public void testAccessToken() throws IOException, GitAPIException {
        GithubExtension githubExtension = new GithubExtension();
        githubExtension.setAccessToken(new File(System.getProperty("user.home") + "/.ssh/id_rsa"));
        githubExtension.setDebug(true);

        ResourcesExtension resourcesExtension = new ResourcesExtension();
        resourcesExtension.setRepoUrl("https://github.com/Blizzity/libraries");
        resourcesExtension.setBranch("main");

        Logger logger = new Logger(githubExtension);
        GitHub gitHub = new GitHub(logger, resourcesExtension, githubExtension);

        File path = GradleUtils.getGradleHome().resolve("resources").resolve(gitHub.getResourceRepoOwner() + "-" + gitHub.getResourceRepoName()).toFile();
        gitHub.cloneOrPullRepository(path, resourcesExtension.getBranch());
    }
}
