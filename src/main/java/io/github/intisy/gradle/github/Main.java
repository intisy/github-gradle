package io.github.intisy.gradle.github;

import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.impl.Gradle;
import io.github.intisy.gradle.github.utils.FileUtils;
import io.github.intisy.gradle.github.utils.GradleUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Main class.
 */
class Main implements Plugin<Project> {
	/**
	 * Applies all the project stuff.
	 */
	public void apply(Project project) {
		GithubExtension githubExtension = project.getExtensions().create("github", GithubExtension.class);
		ResourcesExtension resourcesExtension = project.getExtensions().create("resources", ResourcesExtension.class);
		Logger logger = new Logger(githubExtension, project);

		Configuration githubImplementation = project.getConfigurations().create("githubImplementation");

		JavaPluginConvention javaConvention = project.getConvention()
				.getPlugin(JavaPluginConvention.class);

		SourceSet main = javaConvention.getSourceSets()
				.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

		Set<File> resourceDirs = main.getResources().getSrcDirs();
		GitHub gitHub = new GitHub(logger, resourcesExtension, githubExtension);

		Task processGitHubResources = project.getTasks().create("processGitHubResources", task -> task.doLast(t -> {
            logger.debug("Process resource event called on " + project.getName());
            if (resourcesExtension.getRepoUrl() != null) {
                logger.debug("Found an repository in the resource extension");

                File path = GradleUtils.getGradleHome().resolve("resources").resolve(gitHub.getResourceRepoOwner() + "-" + gitHub.getResourceRepoName()).toFile();

                for (File dir : resourceDirs) {
                    try {
                        gitHub.cloneOrPullRepository(path, resourcesExtension.getBranch());

                        if (resourcesExtension.isBuildOnly()) {
                            dir = project.getBuildDir().toPath().resolve("resources").toFile();
                        }

                        FileUtils.deleteDirectory(dir.toPath());

                        if (dir.mkdirs()) {
                            logger.debug("Copying resources from " + path + " to: " + dir);
                            FileUtils.copyDirectory(path.toPath().resolve(resourcesExtension.getPath()).resolve(dir.getParentFile().getName()), dir.toPath());
                        } else {
                            logger.error("Failed to create directory: " + dir);
                        }
                    } catch (GitAPIException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }));

		project.getPlugins().withType(JavaPlugin.class, (Action<? super JavaPlugin>) plugin -> project.getTasks().named("processResources", Copy.class, processResources -> {
            logger.debug("Process resource event found on " + project.getName());
            processResources.dependsOn(processGitHubResources);
        }));

		project.afterEvaluate(proj -> githubImplementation.getDependencies().all(dependency -> {
			File jar = gitHub.getAsset(dependency.getGroup(), dependency.getName(), dependency.getVersion());

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
						String newVersion = gitHub.getLatestVersion(group, name);
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
}
