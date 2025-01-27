package io.github.intisy.gradle.github;

import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.impl.Gradle;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Main class.
 */
class Main implements org.gradle.api.Plugin<Project> {
	/**
	 * Applies all the project stuff.
	 */
    public void apply(Project project) {
		GithubExtension extension = project.getExtensions().create("github", GithubExtension.class);
		Logger logger = new Logger(project);
		Configuration githubImplementation = project.getConfigurations().create("githubImplementation");
		project.afterEvaluate(proj -> githubImplementation.getDependencies().all(dependency -> {
            File jar = GitHub.getAsset(logger, dependency.getName(), dependency.getGroup(), dependency.getVersion(), getGitHub(logger, extension));
            project.getDependencies().add("implementation", project.files(jar));
        }));
		project.getTasks().register("printGithubDependencies", task -> {
			task.setGroup("github");
			task.setDescription("Implement an github dependency");
			task.doLast(t -> {
				for (Dependency dependency : githubImplementation.getAllDependencies()) {
					String group = dependency.getGroup();
					String name = dependency.getName();
					String version = dependency.getVersion();
					logger.log("Github Dependency named " + name + " version " + version + " from user" + group);
				}
			});
		});
		if (project == project.getRootProject())
			project.getRootProject().getTasks().register("updateGithubDependencies", task -> {
				task.setGroup("github");
				task.setDescription("Updates all GitHub dependencies");
				task.doLast(t -> {
					boolean refresh = false;
					Set<Dependency> dependencyList = new HashSet<>();
					project.getAllprojects().forEach(p -> {
						if (!p.equals(project.getRootProject())) {
							dependencyList.addAll(p.getConfigurations().getByName("githubImplementation").getAllDependencies());
						}
					});
					logger.debug("Updating GitHub dependencies: " + dependencyList);
					for (Dependency dependency : dependencyList) {
						String group = dependency.getGroup();
						String name = dependency.getName();
						String version = dependency.getVersion();
						logger.debug("Updating GitHub dependency: " + name);
						String newVersion = GitHub.getLatestVersion(logger, group, name, getGitHub(logger, extension));
						if (version != null && !version.equals(newVersion)) {
							logger.log("Updating GitHub dependency " + group + "/" + name + " (" + version + " -> " + newVersion + ")");
							Gradle.modifyBuildFile(project, group + ":" + name + ":" + version, group + ":" + name + ":" + newVersion);
							refresh = true;
						} else {
							logger.log("Dependency " + group + "/" + name + " is already up to date");
						}
					}
					if (refresh)
						Gradle.safeSoftRefreshGradle(project);
				});
		});
    }

	public static org.kohsuke.github.GitHub getGitHub(Logger logger, GithubExtension extension) {
		org.kohsuke.github.GitHub github;
		try {
			if (extension.getAccessToken() == null) {
				github = org.kohsuke.github.GitHub.connectAnonymously();
				logger.debug("Pulling from github anonymously");
			} else {
				github = org.kohsuke.github.GitHub.connectUsingOAuth(extension.getAccessToken());
				logger.debug("Pulling from github using OAuth");
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return github;
	}
}
