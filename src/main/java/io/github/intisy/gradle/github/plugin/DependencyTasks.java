package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.extension.GithubExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import java.util.Set;

/**
 * Registers the {@code printGithubDependencies} and {@code updateGithubDependencies} tasks.
 */
public class DependencyTasks {

	/**
	 * Registers {@code printGithubDependencies} on every project, and {@code updateGithubDependencies}
	 * on the root project only (it rewrites every subproject's build file in one pass).
	 *
	 * @param project the project to register the tasks on.
	 * @param logger receives diagnostic output.
	 * @param githubExtension the extension supplying {@code resilience.skipOnRateLimit}.
	 * @param releases the client used to look up each dependency's latest version.
	 */
	public static void apply(Project project, Logger logger, GithubExtension githubExtension, Releases releases) {
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
							newVersion = releases.latestVersion(group, name);
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
