package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.utils.GradleUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import java.util.Set;

/**
 * Registers the {@code printGithubDependencies} and {@code updateGithubDependencies} tasks.
 */
public class DependencyTasks {

	/**
	 * The subset of the GitHub client this class needs, kept free of {@code impl} types so the
	 * layering rule (only {@code api}/{@code impl} may name {@code impl}) still holds.
	 */
	public interface VersionLookup {
		String getLatestVersion(String repoOwner, String repoName);
	}

	public static void apply(Project project, Logger logger, GithubExtension githubExtension, VersionLookup gitHub) {
		project.getTasks().register("printGithubDependencies", task -> {
			task.setGroup("github");
			task.setDescription("Prints all GitHub dependencies across all configurations");
			task.doLast(t -> {
				for (Dependency dependency : GithubConfigurations.getAllDependencies(project)) {
					logger.log("Github Dependency named " + dependency.getName() + " version " + dependency.getVersion() + " from user" + dependency.getGroup());
				}
			});
		});

		if (project == project.getRootProject())
			project.getTasks().register("updateGithubDependencies", task -> {
				task.setGroup("github");
				task.setDescription("Updates all GitHub dependencies");
				task.doLast(t -> {
					boolean refresh = false;
					Set<Dependency> dependencyList = GithubConfigurations.getAllDependencies(project);
					logger.debug("Updating GitHub dependencies: " + dependencyList);
					for (Dependency dependency : dependencyList) {
						String group = dependency.getGroup();
						String name = dependency.getName();
						String version = dependency.getVersion();
						logger.debug("Updating GitHub dependency: " + name);
						String newVersion;
						try {
							newVersion = gitHub.getLatestVersion(group, name);
						} catch (RateLimitException e) {
							if (!githubExtension.getResilience().isSkipOnRateLimit()) {
								throw e;
							}
							logger.warn("Skipping update check for " + group + "/" + name
								+ " due to a rate limit (github.skipOnRateLimit = true).");
							continue;
						}
						if (newVersion == null) {
							logger.warn("Could not determine the latest version for " + group + "/" + name
								+ "; keeping the current version " + version + ".");
						} else if (version != null && !version.equals(newVersion)) {
							logger.log("Updating GitHub dependency " + group + "/" + name + " (" + version + " -> " + newVersion + ")");
							for (Project p : GradleUtils.getAllProjectsRecursive(project)) {
								BuildFileEditor.modifyBuildFile(p, group + ":" + name + ":" + version, group + ":" + name + ":" + newVersion);
							}
							refresh = true;
						} else {
							logger.log("Dependency " + group + "/" + name + " is already up to date");
						}
					}
					if (refresh) BuildFileEditor.safeSoftRefreshGradle(project);
				});
			});
	}
}
