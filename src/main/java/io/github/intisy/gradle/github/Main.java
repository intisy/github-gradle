package io.github.intisy.gradle.github;

import io.github.intisy.gradle.github.impl.GitHub;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.io.IOException;

/**
 * Main class.
 */
class Main implements org.gradle.api.Plugin<Project> {
	/**
	 * Applies all the project stuff.
	 */
    public void apply(Project project) {
		GithubExtension extension = project.getExtensions().create("github", GithubExtension.class);
		Configuration githubImplementation = project.getConfigurations().create("githubImplementation");
		project.afterEvaluate(proj -> {
			try {
				org.kohsuke.github.GitHub github;
				if (extension.getAccessToken() == null) {
					github = org.kohsuke.github.GitHub.connectAnonymously();
					System.out.println("Pulling from github anonymously");
				} else {
					github = org.kohsuke.github.GitHub.connectUsingOAuth(extension.getAccessToken());
					System.out.println("Pulling from github using OAuth");
				}
				githubImplementation.getDependencies().all(dependency -> {
					File jar = GitHub.getAsset(dependency.getName(), dependency.getGroup(), dependency.getVersion(), github);
					project.getDependencies().add("implementation", project.files(jar));
				});
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		project.getTasks().register("printGithubDependencies", task -> {
			task.setGroup("github");
			task.setDescription("Implement an github dependency");
			task.doLast(t -> {
				for (Dependency dependency : githubImplementation.getAllDependencies()) {
					String group = dependency.getGroup();
					String name = dependency.getName();
					String version = dependency.getVersion();
					System.out.println("Github Dependency named " + name + " version " + version + " from user" + group);
				}
			});
		});
    }
}
