package io.github.intisy.gradle.github;

import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.extension.ArtifactEntry;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.extension.PublishExtension;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.plugin.BuildFileEditor;
import io.github.intisy.gradle.github.plugin.DependencyMetadata;
import io.github.intisy.gradle.github.plugin.DependencyResolution;
import io.github.intisy.gradle.github.plugin.GithubConfigurations;
import io.github.intisy.gradle.github.plugin.ResourceSync;
import io.github.intisy.gradle.github.utils.FileUtils;
import io.github.intisy.gradle.github.utils.GradleUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

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

		GithubConfigurations.apply(project);

		GitHub gitHub = new GitHub(logger, resourcesExtension, githubExtension);

		ResourceSync.apply(project, logger, resourcesExtension, new ResourceSync.RepoSync() {
			public String getResourceRepoOwner() {
				return gitHub.getResourceRepoOwner();
			}
			public String getResourceRepoName() {
				return gitHub.getResourceRepoName();
			}
			public void cloneOrPullRepository(File path, String branch) throws GitAPIException, IOException {
				gitHub.cloneOrPullRepository(path, branch);
			}
		});

		DependencyResolution.apply(project, logger, githubExtension, new DependencyResolution.DependencyAssetResolver() {
			public void getAssetWithTransitives(String repoOwner, String repoName, String version, Set<String> resolved, List<File> collected) {
				gitHub.getAssetWithTransitives(repoOwner, repoName, version, resolved, collected);
			}
			public void getAllModuleAssets(String repoOwner, String repoName, String version, List<File> collected) {
				gitHub.getAllModuleAssets(repoOwner, repoName, version, collected);
			}
			public File getAssetWithClassifier(String repoOwner, String repoName, String version, String classifier) {
				return gitHub.getAssetWithClassifier(repoOwner, repoName, version, classifier);
			}
		});

		DependencyMetadata.apply(project, logger);

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
}
