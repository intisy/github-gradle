package io.github.intisy.gradle.github;

import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.impl.Gradle;
import io.github.intisy.gradle.github.utils.FileUtils;
import io.github.intisy.gradle.github.utils.GradleUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.impldep.org.eclipse.jgit.api.errors.GitAPIException;

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
		ResourcesExtension resourcesExtension = project.getExtensions().create("resources", ResourcesExtension.class);
		GithubExtension githubExtension = project.getExtensions().create("github", GithubExtension.class);
		Logger logger = new Logger(project);
		Configuration githubImplementation = project.getConfigurations().create("githubImplementation");
		Task processGitHubResources = project.getTasks().create("processGitHubResources", task -> {
			task.doLast(t -> {
				logger.debug("Process resource event called on " + project.getName());
				if (resourcesExtension.getRepo() != null) {
					logger.debug("Found an repository in the resource extension");
					JavaPluginConvention javaConvention = project.getConvention()
							.getPlugin(JavaPluginConvention.class);
					SourceSet main = javaConvention.getSourceSets()
							.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
					Set<File> resourceDirs = main.getResources().getSrcDirs();
					String[] repoParts = resourcesExtension.getRepo().split("/");
					for (File dir : resourceDirs) {
						try {
							File path = GradleUtils.getGradleHome().resolve("resources").resolve(repoParts[3] + "-" + repoParts[4]).toFile();
							GitHub.cloneOrPullRepository(logger, path, repoParts[3], repoParts[4], githubExtension.getAccessToken());
							if (resourcesExtension.isBuildOnly()) {
								dir = project.getBuildDir().toPath().resolve("resources").resolve(dir.getParentFile().getName()).toFile();
							}
							FileUtils.deleteDirectory(dir.toPath());
							if (dir.mkdirs()) {
								logger.debug("Copying resources from " + path + " to: " + dir);
								FileUtils.copyDirectory(path.toPath(), dir.toPath());
							} else {
								logger.error("Failed to create directory: " + dir);
							}
						} catch (GitAPIException | IOException e) {
							throw new RuntimeException(e);
						}
					}
				}
			});
		});
		project.getPlugins().withType(JavaPlugin.class, (Action<? super JavaPlugin>) plugin -> {
			project.getTasks().named("processResources", Copy.class, processResources -> {
				logger.debug("Process resource event found on " + project.getName());
				processResources.dependsOn(processGitHubResources);
			});
		});
		project.afterEvaluate(proj -> githubImplementation.getDependencies().all(dependency -> {
            File jar = GitHub.getAsset(logger, dependency.getName(), dependency.getGroup(), dependency.getVersion(), getGitHub(logger, githubExtension));
            project.getDependencies().add("implementation", project.files(jar));
        }));
		project.getTasks().register("printGithubDependencies", task -> {
			task.setGroup("github");
			task.setDescription("Implement an github dependency");
			task.doLast(t -> {
				for (Dependency dependency : getDependencies(project)) {
					String group = dependency.getGroup();
					String name = dependency.getName();
					String version = dependency.getVersion();
					logger.log("Github Dependency named " + name + " version " + version + " from user" + group);
				}
			});
		});
		if (project == project.getRootProject())
			project.getTasks().register("updateGithubDependencies", task -> {
				task.setGroup("github");
				task.setDescription("Updates all GitHub dependencies");
				task.doLast(t -> {
					boolean refresh = false;
					Set<Dependency> dependencyList = getDependencies(project);
					logger.debug("Updating GitHub dependencies: " + dependencyList);
					for (Dependency dependency : dependencyList) {
						String group = dependency.getGroup();
						String name = dependency.getName();
						String version = dependency.getVersion();
						logger.debug("Updating GitHub dependency: " + name);
						String newVersion = GitHub.getLatestVersion(logger, group, name, getGitHub(logger, githubExtension));
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

	public Set<Dependency> getDependencies(Project project) {
		Set<Dependency> dependencyList = new HashSet<>();
		project.getAllprojects().forEach(p -> {
			if (!p.equals(project.getRootProject())) {
				dependencyList.addAll(p.getConfigurations().getByName("githubImplementation").getAllDependencies());
			}
		});
		dependencyList.addAll(project.getConfigurations().getByName("githubImplementation").getAllDependencies());
		return dependencyList;
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
