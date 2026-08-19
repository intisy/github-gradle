package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Logger;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

/**
 * Generates {@code META-INF/github-dependencies.json} from the githubImplementation dependencies.
 */
public class DependencyMetadata {

	/**
	 * Registers the {@code generateGithubDependencyMetadata} task and wires it ahead of
	 * {@code processResources} so every jar built by this project embeds the metadata that
	 * {@link io.github.intisy.gradle.github.api.capability.Releases#declaredDependencies} later reads back.
	 *
	 * @param project the project to register the task on; a no-op unless the {@code java} plugin is applied.
	 * @param logger receives diagnostic output.
	 */
	public static void apply(Project project, Logger logger) {
		project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
			SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class)
				.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
			Task generateMeta = project.getTasks().create("generateGithubDependencyMetadata", task -> {
				task.setGroup("github");
				task.setDescription("Generates META-INF/github-dependencies.json from githubImplementation dependencies");
				task.doLast(t -> {
					Set<Dependency> deps = GithubConfigurations.getDependencies(project);
					if (deps.isEmpty()) { logger.debug("No githubImplementation dependencies to write metadata for."); return; }
					StringBuilder json = new StringBuilder("[\n");
					boolean first = true;
					for (Dependency dep : deps) {
						if (!first) json.append(",\n");
						first = false;
						json.append("  {\"group\":\"").append(dep.getGroup())
							.append("\",\"name\":\"").append(dep.getName())
							.append("\",\"version\":\"").append(dep.getVersion())
							.append("\"}");
					}
					json.append("\n]");
					File outputDir = new File(project.getLayout().getBuildDirectory().getAsFile().get(), "generated/resources/github-deps/META-INF");
					if (!outputDir.exists() && !outputDir.mkdirs()) { throw new RuntimeException("Failed to create directory: " + outputDir); }
					File outputFile = new File(outputDir, "github-dependencies.json");
					try (FileWriter writer = new FileWriter(outputFile)) {
						writer.write(json.toString());
						logger.debug("Wrote github-dependencies.json: " + outputFile.getAbsolutePath());
					} catch (IOException e) {
						throw new RuntimeException("Failed to write github-dependencies.json", e);
					}
				});
			});
			main.getResources().srcDir(new File(project.getLayout().getBuildDirectory().getAsFile().get(), "generated/resources/github-deps"));
			project.getTasks().named("processResources", Copy.class, processResources -> processResources.dependsOn(generateMeta));
		});
	}
}
