package io.github.intisy.gradle.github;

import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.extension.ArtifactEntry;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.extension.PublishExtension;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.impl.Gradle;
import io.github.intisy.gradle.github.impl.RateLimitException;
import io.github.intisy.gradle.github.utils.FileUtils;
import io.github.intisy.gradle.github.utils.GradleUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Main plugin class.
 */
class Main implements Plugin<Project> {

	/** Names of all GitHub dependency configurations created by this plugin, in declaration order. */
	private static final List<String> GITHUB_CONFIGS = Collections.unmodifiableList(Arrays.asList(
		"githubImplementation",
		"githubApi",
		"githubCompileOnly",
		"githubCompileOnlyApi",
		"githubRuntimeOnly"
	));

	/**
	 * Maps each GitHub configuration to the native Gradle configuration it feeds.
	 * {@code api} and {@code compileOnlyApi} require the {@code java-library} plugin.
	 */
	private static final Map<String, String> GITHUB_TO_GRADLE;
	static {
		Map<String, String> m = new HashMap<String, String>();
		m.put("githubImplementation",  "implementation");
		m.put("githubApi",             "api");
		m.put("githubCompileOnly",     "compileOnly");
		m.put("githubCompileOnlyApi",  "compileOnlyApi");
		m.put("githubRuntimeOnly",     "runtimeOnly");
		GITHUB_TO_GRADLE = Collections.unmodifiableMap(m);
	}

	/** Gradle configurations that require the {@code java-library} plugin. */
	private static final Set<String> JAVA_LIBRARY_CONFIGS = new HashSet<String>(Arrays.asList(
		"api", "compileOnlyApi"
	));

	public void apply(Project project) {
		GithubExtension githubExtension = project.getExtensions().create("github", GithubExtension.class);
		ResourcesExtension resourcesExtension = githubExtension.getResources();

		PublishExtension publishExtension = githubExtension.getPublish();
		project.getExtensions().add("publishGithub", publishExtension);

		Logger logger = new Logger(githubExtension, project);

		for (String cfgName : GITHUB_CONFIGS) {
			project.getConfigurations().create(cfgName);
		}

		GitHub gitHub = new GitHub(logger, resourcesExtension, githubExtension);

		project.getPlugins().withType(JavaPlugin.class, (Action<? super JavaPlugin>) javaPlugin -> {
			JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
			SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
			Set<File> resourceDirs = main.getResources().getSrcDirs();

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

			project.getTasks().named("processResources", Copy.class, processResources -> {
				logger.debug("Process resource event found on " + project.getName());
				processResources.dependsOn(processGitHubResources);
			});
		});

		project.afterEvaluate(proj -> {
			Set<String> resolved = new HashSet<String>();
			List<File> allJars = new ArrayList<File>();
			for (String cfgName : GITHUB_CONFIGS) {
				String nativeCfg = GITHUB_TO_GRADLE.get(cfgName);
				boolean needsJavaLibrary = JAVA_LIBRARY_CONFIGS.contains(nativeCfg);
				if (needsJavaLibrary && !proj.getPlugins().hasPlugin("java-library")) {
					continue;
				}
				Configuration cfg = proj.getConfigurations().getByName(cfgName);
				for (Dependency dependency : cfg.getDependencies()) {
					try {
						String classifier = extractClassifier(dependency);
						List<File> jars = new ArrayList<File>();
						if (classifier.isEmpty()) {
							gitHub.getAssetWithTransitives(dependency.getGroup(), dependency.getName(), dependency.getVersion(), resolved, jars);
						} else if (classifier.equals("all")) {
							gitHub.getAllModuleAssets(dependency.getGroup(), dependency.getName(), dependency.getVersion(), jars);
						} else {
							File jar = gitHub.getAssetWithClassifier(dependency.getGroup(), dependency.getName(), dependency.getVersion(), classifier);
							if (jar != null) jars.add(jar);
						}
						for (File jar : jars) {
							proj.getDependencies().add(nativeCfg, proj.files(jar));
						}
					} catch (RateLimitException e) {
						if (!githubExtension.getResilience().isSkipOnRateLimit()) {
							throw e;
						}
						logger.warn("Rate limited resolving " + dependency.getGroup() + ":" + dependency.getName()
							+ ":" + dependency.getVersion() + " and no cached copy is available; skipping it "
							+ "(github.skipOnRateLimit = true). The compile classpath may be incomplete.");
					}
				}
			}
		});

		project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
			SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class)
				.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
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
			task.setDescription("Prints all GitHub dependencies across all configurations");
			task.doLast(t -> {
				for (Dependency dependency : getAllDependencies(project)) {
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
					Set<Dependency> dependencyList = getAllDependencies(project);
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
			task.setDescription("Creates a GitHub release and uploads the project JAR(s)");
			task.dependsOn((Callable<List<Task>>) () -> {
				List<Task> dependencies = new ArrayList<Task>();
				Task buildTask = project.getTasks().findByName("build");
				if (buildTask != null) dependencies.add(buildTask);
				if (hasModuleArtifact(publishExtension)) {
					for (Project sub : project.getSubprojects()) {
						Task jarTask = sub.getTasks().findByName("jar");
						if (jarTask != null) dependencies.add(jarTask);
					}
				}
				return dependencies;
			});
			task.doLast(t -> {
				String version = publishExtension.getVersion() != null
					        ? publishExtension.getVersion()
					        : project.getVersion().toString();
				if (version.equals("unspecified")) {
					throw new RuntimeException("Cannot publish: project.version is unspecified. "
					        + "Set version in your build.gradle, or set publishGithub { version = \"1.0.0\" }.");
				}

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
								String tag = publishExtension.getTag() != null
					        ? publishExtension.getTag()
					        : version;
				String releaseName = publishExtension.getReleaseName();

				logger.log("Publishing " + owner + "/" + repo + " tag " + tag + " version " + version);

				JsonObject release = gitHub.createRelease(owner, repo, tag, releaseName);
				String uploadUrl = release.get("upload_url").getAsString();

				List<ArtifactEntry> entries = expandArtifacts(publishExtension.getArtifacts(), project, repo, logger);
				if (!entries.isEmpty()) {
					for (ArtifactEntry entry : entries) {
						File jar = entry.getJar();
						if (jar == null) {
							throw new RuntimeException("An artifact entry in publishGithub.artifacts has no jar configured.");
						}
						if (!jar.exists()) {
							throw new RuntimeException("Artifact JAR does not exist: " + jar.getAbsolutePath());
						}
						String assetName = buildAssetName(repo, entry.getClassifier());
						logger.log("Uploading artifact: " + jar.getName() + " as " + assetName);
						try {
							gitHub.uploadReleaseAsset(uploadUrl, jar, assetName);
						} catch (IOException e) {
							throw new RuntimeException("Failed to upload asset " + assetName + ": " + e.getMessage(), e);
						}
					}
				} else {
					File jarToUpload = resolveSingleJar(publishExtension, project, logger);
					String assetName = repo + ".jar";
					logger.log("Uploading: " + jarToUpload.getName() + " as " + assetName);
					try {
						gitHub.uploadReleaseAsset(uploadUrl, jarToUpload, assetName);
					} catch (IOException e) {
						throw new RuntimeException("Failed to upload asset: " + e.getMessage(), e);
					}
				}
				logger.log("Published " + owner + "/" + repo + " " + version + " successfully.");
			});
		});
	}

	/**
	 * Builds the GitHub release asset file name for the given repo and classifier.
	 *
	 * @param repo       the repository name
	 * @param classifier the artifact classifier (blank means default)
	 * @return e.g. {@code "my-repo.jar"} or {@code "my-repo-api.jar"}
	 */
	private String buildAssetName(String repo, String classifier) {
		if (classifier == null || classifier.isEmpty()) {
			return repo + ".jar";
		}
		return repo + "-" + classifier + ".jar";
	}

	/**
	 * Builds the artifact list for multi-module publishing: one entry per subproject, using its {@code jar}
	 * task output and a classifier equal to the subproject name with the {@code <repo>-} prefix stripped
	 * (so {@code dough-common} in repo {@code dough} uploads as {@code dough-common.jar}, not
	 * {@code dough-dough-common.jar}).
	 *
	 * @param project the (root) project whose subprojects are published
	 * @param repo    the repository name (used to strip the module prefix)
	 * @param logger  the logger
	 * @return one {@link ArtifactEntry} per subproject that produces a jar
	 */
	private List<ArtifactEntry> buildModuleArtifacts(Project project, String repo, Logger logger) {
		List<ArtifactEntry> entries = new ArrayList<ArtifactEntry>();
		for (Project sub : project.getSubprojects()) {
			Task jarTask = sub.getTasks().findByName("jar");
			if (!(jarTask instanceof Jar)) {
				logger.debug("Skipping subproject without a jar task: " + sub.getName());
				continue;
			}
			File jar = ((Jar) jarTask).getArchiveFile().get().getAsFile();
			String name = sub.getName();
			String classifier = name.startsWith(repo + "-") ? name.substring(repo.length() + 1) : name;
			ArtifactEntry entry = new ArtifactEntry();
			entry.setJar(jar);
			entry.setClassifier(classifier);
			entries.add(entry);
			logger.debug("Module artifact: " + sub.getName() + " -> " + buildAssetName(repo, classifier));
		}
		if (entries.isEmpty()) {
			throw new RuntimeException("An artifact { modules = true } entry was declared but no subprojects with a jar task were found.");
		}
		return entries;
	}

	/**
	 * Expands the declared artifact entries into the final upload list. Each {@code modules = true} entry is
	 * replaced by one entry per subproject (see {@link #buildModuleArtifacts}); all other entries pass through
	 * unchanged, so module assets and regular classified jars can be published together in one release.
	 *
	 * @param declared the artifact entries configured on the extension
	 * @param project  the (root) project whose subprojects back any module entries
	 * @param repo     the repository name
	 * @param logger   the logger
	 * @return the expanded artifact entries to upload
	 */
	private List<ArtifactEntry> expandArtifacts(List<ArtifactEntry> declared, Project project, String repo, Logger logger) {
		List<ArtifactEntry> expanded = new ArrayList<ArtifactEntry>();
		for (ArtifactEntry entry : declared) {
			if (entry.isModules()) {
				expanded.addAll(buildModuleArtifacts(project, repo, logger));
			} else {
				expanded.add(entry);
			}
		}
		return expanded;
	}

	/**
	 * @param publishExtension the publish extension
	 * @return true if any declared artifact entry has {@code modules = true}
	 */
	private boolean hasModuleArtifact(PublishExtension publishExtension) {
		for (ArtifactEntry entry : publishExtension.getArtifacts()) {
			if (entry.isModules()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Resolves the single JAR to upload when no explicit {@code artifacts} list is configured.
	 * Prefers shadow/fat JARs; falls back to the first regular JAR in {@code build/libs/}.
	 *
	 * @param ext     the publish extension
	 * @param project the Gradle project
	 * @param logger  the logger
	 * @return the resolved JAR file (never null)
	 * @throws RuntimeException if no suitable JAR is found
	 */
	private File resolveSingleJar(PublishExtension ext, Project project, Logger logger) {
		if (ext.getJar() != null) {
			if (!ext.getJar().exists()) {
				throw new RuntimeException("Configured publishGithub.jar does not exist: " + ext.getJar().getAbsolutePath());
			}
			logger.log("Using configured jar: " + ext.getJar().getName());
			return ext.getJar();
		}
		File buildLibs = new File(project.getLayout().getBuildDirectory().getAsFile().get(), "libs");
		if (!buildLibs.exists() || !buildLibs.isDirectory()) {
			throw new RuntimeException("No build/libs/ directory found. Run build first, "
			        + "or set publishGithub { jar = file(\"path/to/my.jar\") }.");
		}
		File regularJar = null;
		File fatJar = null;
		File[] files = buildLibs.listFiles();
		if (files != null) {
			for (File f : files) {
				String fname = f.getName();
				if (!fname.endsWith(".jar")) continue;
				if (fname.endsWith("-sources.jar") || fname.endsWith("-javadoc.jar")) continue;
				if (fname.contains("-standalone") || fname.contains("-all") || fname.contains("-shadow")) {
					fatJar = f;
					break;
				}
				if (regularJar == null) regularJar = f;
			}
		}
		File result = fatJar != null ? fatJar : regularJar;
		if (result == null) {
			throw new RuntimeException("No JAR found in " + buildLibs.getAbsolutePath()
			        + " (excluding -sources.jar / -javadoc.jar). "
			        + "Set publishGithub { jar = file(\"path/to/my.jar\") } to specify one explicitly.");
		}
		logger.log("Selected artifact: " + result.getName());
		return result;
	}

	/**
	 * Extracts the classifier from a dependency declared as
	 * {@code "OWNER:REPO:VERSION:CLASSIFIER"}.
	 *
	 * <p>Gradle parses the 4th colon-segment as an artifact classifier accessible via
	 * {@code ExternalDependency.getArtifacts()}. Returns an empty string when no classifier
	 * is present (the common case).
	 *
	 * @param dependency the Gradle dependency
	 * @return the classifier string, or {@code ""} if absent
	 */	private String extractClassifier(Dependency dependency) {
		if (dependency instanceof ExternalDependency) {
			ExternalDependency ext = (ExternalDependency) dependency;
			if (!ext.getArtifacts().isEmpty()) {
				String classifier = ext.getArtifacts().iterator().next().getClassifier();
				return classifier != null ? classifier : "";
			}
		}
		return "";
	}

	/**
	 * @param project the project
	 * @return all github dependency configurations' dependencies across all subprojects
	 */
	public Set<Dependency> getAllDependencies(Project project) {
		return project.getAllprojects().stream().flatMap(p -> getDependencies(p).stream()).collect(Collectors.toSet());
	}

	/**
	 * @param project the project
	 * @return all github dependency configurations' dependencies for this project only
	 */
	public Set<Dependency> getDependencies(Project project) {
		Set<Dependency> all = new LinkedHashSet<Dependency>();
		for (String cfgName : GITHUB_CONFIGS) {
			Configuration cfg = project.getConfigurations().findByName(cfgName);
			if (cfg != null) {
				all.addAll(cfg.getDependencies());
			}
		}
		return all;
	}
}
