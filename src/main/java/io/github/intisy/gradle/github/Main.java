package io.github.intisy.gradle.github;

import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.extension.PublishExtension;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
import io.github.intisy.gradle.github.impl.GitHub;
import io.github.intisy.gradle.github.plugin.DependencyMetadata;
import io.github.intisy.gradle.github.plugin.DependencyResolution;
import io.github.intisy.gradle.github.plugin.DependencyTasks;
import io.github.intisy.gradle.github.plugin.GithubConfigurations;
import io.github.intisy.gradle.github.plugin.PublishTasks;
import io.github.intisy.gradle.github.plugin.ResourceSync;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.dsl.ArtifactHandler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

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

		DependencyTasks.apply(project, logger, githubExtension, new DependencyTasks.VersionLookup() {
			public String getLatestVersion(String repoOwner, String repoName) {
				return gitHub.getLatestVersion(repoOwner, repoName);
			}
		});

		PublishTasks.apply(project, logger, publishExtension, new PublishTasks.ReleasePublisher() {
			public String[] getRemoteOwnerAndRepo(File projectDir) {
				return gitHub.getRemoteOwnerAndRepo(projectDir);
			}
			public JsonObject createRelease(String owner, String repo, String tagName, String releaseName) {
				return gitHub.createRelease(owner, repo, tagName, releaseName);
			}
			public void uploadReleaseAsset(String uploadUrl, File file, String assetName) throws IOException {
				gitHub.uploadReleaseAsset(uploadUrl, file, assetName);
			}
		});
	}
}
