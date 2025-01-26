package io.github.intisy.gradle.github;

import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.impl.Gradle;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;

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
		project.getRootProject().getTasks().register("updateGithubDependencies", task -> {
			task.setGroup("github");
			task.setDescription("Updates all GitHub dependencies");
			task.doLast(t -> {
				logger.debug("Updating all GitHub dependencies");
				boolean refresh = false;
				Set<Dependency> dependencyList = new HashSet<>();
				project.getAllprojects().forEach(p -> {
					if (!p.equals(project.getRootProject())) {
						collectDeclaredDependencies(p, dependencyList);
					}
				});

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
						logger.log("Dependency " + group + "/" + name + " is already up to date (" + version + " -> " + newVersion + ")");
					}
				}
				if (refresh)
					Gradle.safeSoftRefreshGradle(project);
			});
		});
    }

	private void collectDeclaredDependencies(Project project, Set<Dependency> collector) {
		project.getConfigurations().forEach(config -> {
			if (config.isCanBeConsumed() || config.isCanBeResolved()) {
				config.getDependencies().forEach(dependency -> {
					if (isNewDependency(collector, dependency)) {
						collector.add(dependency);
					}
				});
			}
		});
	}

	private boolean isNewDependency(Set<Dependency> existing, Dependency newDep) {
		return existing.stream().noneMatch(d -> areDependenciesEqual(d, newDep));
	}

	private boolean areDependenciesEqual(Dependency d1, Dependency d2) {
		if (d1 instanceof ModuleDependency && d2 instanceof ModuleDependency) {
			ModuleDependency m1 = (ModuleDependency) d1;
			ModuleDependency m2 = (ModuleDependency) d2;

			return m1.getGroup().equals(m2.getGroup()) &&
					m1.getName().equals(m2.getName()) &&
					m1.getVersion().equals(m2.getVersion());
		}
		return d1.equals(d2);
	}

	private String dependencyToString(Dependency dependency) {
		if (dependency instanceof ModuleDependency) {
			ModuleDependency md = (ModuleDependency) dependency;
			return String.format("%s:%s:%s",
					md.getGroup(), md.getName(), md.getVersion());
		}
		return dependency.toString();
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
