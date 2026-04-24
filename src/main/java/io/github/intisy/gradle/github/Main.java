package io.github.intisy.gradle.github;

import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.extension.PublishExtension;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
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
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main plugin class.
 */
class Main implements Plugin<Project> {
	public void apply(Project project) {
		GithubExtension githubExtension = project.getExtensions().create("github", GithubExtension.class);
		ResourcesExtension resourcesExtension = githubExtension.getResources();

		PublishExtension publishExtension = githubExtension.getPublish();
		project.getExtensions().add("publishGithub", publishExtension);

		Logger logger = new Logger(githubExtension, project);
		Configuration githubImplementation = project.getConfigurations().create("githubImplementation");
		JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
		SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
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

		project.afterEvaluate(proj -> {
			Set<String> resolved = new HashSet<>();
			List<File> allJars = new ArrayList<>();
			for (Dependency dependency : githubImplementation.getDependencies()) {
				gitHub.getAssetWithTransitives(dependency.getGroup(), dependency.getName(), dependency.getVersion(), resolved, allJars);
			}
			for (File jar : allJars) {
				project.getDependencies().add("implementation", project.files(jar));
			}
		});

		project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
			Task generateMeta = project.getTasks().create("generateGithubDependencyMetadata", task -> {
				task.setGroup("github");
				task.setDescription("Generates META-INF/github-dependencies.json from githubImplementation dependencies");
				task.doLast(t -> {
					Set<Dependency> deps = getDependencies(project);
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

		project.getTasks().register("printGithubDependencies", task -> {
			task.setGroup("github");
			task.setDescription("Implement an github dependency");
			task.doLast(t -> {
				for (Dependency dependency : getAllDependencies(project)) {
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
					Set<Dependency> dependencyList = getAllDependencies(project);
					logger.debug("Updating GitHub dependencies: " + dependencyList);
					for (Dependency dependency : dependencyList) {
						String group = dependency.getGroup();
						String name = dependency.getName();
						String version = dependency.getVersion();
						logger.debug("Updating GitHub dependency: " + name);
						String newVersion = gitHub.getLatestVersion(group, name);
						if (version != null && !version.equals(newVersion)) {
							logger.log("Updating GitHub dependency " + group + "/" + name + " (" + version + " -> " + newVersion + ")");
							for (Project p : GradleUtils.getAllProjectsRecursive(project)) {
								Gradle.modifyBuildFile(p, group + ":" + name + ":" + version, group + ":" + name + ":" + newVersion);
							}
							refresh = true;
						} else {
							logger.log("Dependency " + group + "/" + name + " is already up to date");
						}
					}
					if (refresh) Gradle.safeSoftRefreshGradle(project);
				});
			});

		project.getTasks().register("publishGithub", task -> {
			task.setGroup("github");
			task.setDescription("Creates a GitHub release and uploads the project JAR");
			task.dependsOn("build");
			task.doLast(t -> {
				// --- Version: publishGithub extension > project.version ---
				String version = publishExtension.getVersion() != null
					        ? publishExtension.getVersion()
					        : project.getVersion().toString();
				if (version.equals("unspecified")) {
					throw new RuntimeException("Cannot publish: project.version is unspecified. "
					        + "Set version in your build.gradle, or set publishGithub { version = \"1.0.0\" }.");
				}

				// --- Owner/repo: publishGithub extension > git remote origin ---
				String owner;
				String repo;
				if (publishExtension.getOwner() != null && publishExtension.getRepo() != null) {
					owner = publishExtension.getOwner();
					repo  = publishExtension.getRepo();
				} else {
					String[] ownerRepo = gitHub.getRemoteOwnerAndRepo(project.getProjectDir());
					owner = publishExtension.getOwner() != null ? publishExtension.getOwner() : ownerRepo[0];
					repo  = publishExtension.getRepo()  != null ? publishExtension.getRepo()  : ownerRepo[1];
				}
				logger.log("Publishing " + owner + "/" + repo + " version " + version);

				// --- JAR: publishGithub extension > auto-select from build/libs/ ---
				File jarToUpload = publishExtension.getJar();
				if (jarToUpload != null) {
					if (!jarToUpload.exists()) {
						throw new RuntimeException("Configured publishGithub.jar does not exist: " + jarToUpload.getAbsolutePath());
					}
					logger.log("Using configured jar: " + jarToUpload.getName());
				} else {
					File buildLibs = new File(project.getLayout().getBuildDirectory().getAsFile().get(), "libs");
					if (!buildLibs.exists() || !buildLibs.isDirectory()) {
						throw new RuntimeException("No build/libs/ directory found. Run build first, "
						        + "or set publishGithub { jar = file(\"path/to/my.jar\") }.");
					}
					File regularJar = null;
					File[] files = buildLibs.listFiles();
					if (files != null) {
						for (File f : files) {
							String fname = f.getName();
							if (!fname.endsWith(".jar")) continue;
							if (fname.endsWith("-sources.jar") || fname.endsWith("-javadoc.jar")) continue;
							if (fname.contains("-standalone") || fname.contains("-all") || fname.contains("-shadow")) {
								jarToUpload = f; break;
							}
							if (regularJar == null) regularJar = f;
						}
					}
					if (jarToUpload == null) jarToUpload = regularJar;
					if (jarToUpload == null) {
						throw new RuntimeException("No JAR found in " + buildLibs.getAbsolutePath()
						        + " (excluding -sources.jar / -javadoc.jar). "
						        + "Set publishGithub { jar = file(\"path/to/my.jar\") } to specify one explicitly.");
					}
					logger.log("Selected artifact: " + jarToUpload.getName());
				}

				// --- Create release and upload ---
				JsonObject release = gitHub.createRelease(owner, repo, version);
				String uploadUrl = release.get("upload_url").getAsString();
				String assetName = repo + ".jar";
				try {
					gitHub.uploadReleaseAsset(uploadUrl, jarToUpload, assetName);
				} catch (IOException e) {
					throw new RuntimeException("Failed to upload asset: " + e.getMessage(), e);
				}
				logger.log("Published " + owner + "/" + repo + " " + version + " successfully.");
			});
		});
	}

	/**
	 * @param project the project
	 * @return all githubImplementation dependencies across all subprojects
	 */
	public Set<Dependency> getAllDependencies(Project project) {
		return project.getAllprojects().stream().flatMap(p -> getDependencies(p).stream()).collect(Collectors.toSet());
	}

	/**
	 * @param project the project
	 * @return githubImplementation dependencies for this project only
	 */
	public Set<Dependency> getDependencies(Project project) {
		return new LinkedHashSet<>(project.getConfigurations().getByName("githubImplementation").getDependencies());
	}
}
