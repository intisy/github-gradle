package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.api.RemoteRepo;
import io.github.intisy.gradle.github.api.Repositories;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.utils.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Syncs the configured GitHub resource repository into the project's resources before they are processed.
 */
public class ResourceSync {

	public static void apply(Project project, Logger logger, ResourcesExtension resourcesExtension, Repositories repositories) {
		project.getPlugins().withType(JavaPlugin.class, (Action<? super JavaPlugin>) javaPlugin -> {
			JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
			SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
			Set<File> resourceDirs = main.getResources().getSrcDirs();

			Task processGitHubResources = project.getTasks().create("processGitHubResources", task -> task.doLast(t -> {
				logger.debug("Process resource event called on " + project.getName());
				if (resourcesExtension.getRepoUrl() != null) {
					logger.debug("Found an repository in the resource extension");
					RemoteRepo configuredRepo = repositories.configuredRepo();
					if (configuredRepo.getOwner() == null || configuredRepo.getRepo() == null) {
						throw new IllegalStateException("Variable resourcesExtension.repoUrl is not configured.");
					}
					File path = FileUtils.getGradleHome().resolve("resources").resolve(configuredRepo.getOwner() + "-" + configuredRepo.getRepo()).toFile();
					for (File dir : resourceDirs) {
						try {
							repositories.cloneOrPull(path, configuredRepo.getOwner(), configuredRepo.getRepo(), resourcesExtension.getBranch());
							if (resourcesExtension.isBuildOnly()) {
								dir = project.getLayout().getBuildDirectory().getAsFile().get().toPath()
								        .resolve("resources").resolve(dir.getParentFile().getName()).toFile();
							}
							FileUtils.deleteDirectory(dir.toPath());
							if (!resourcesExtension.getPath().equals("/") && !resourcesExtension.getPath().isEmpty())
								path = path.toPath().resolve(resourcesExtension.getPath()).toFile();
							if (dir.mkdirs()) {
								logger.debug("Copying resources from " + path + " to: " + dir);
								FileUtils.copyDirectory(path.toPath(), dir.toPath());
							} else {
								logger.error("Failed to create directory: " + dir);
							}
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				}
			}));

			project.getTasks().named("processResources", Copy.class, processResources -> {
				logger.debug("Process resource event found on " + project.getName());
				processResources.dependsOn(processGitHubResources);
			});
		});
	}
}
