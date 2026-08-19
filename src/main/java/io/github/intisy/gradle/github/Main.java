package io.github.intisy.gradle.github;

import io.github.intisy.gradle.github.api.GitHubApi;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.plugin.extension.PublishExtension;
import io.github.intisy.gradle.github.api.config.ResourcesExtension;
import io.github.intisy.gradle.github.plugin.DependencyMetadata;
import io.github.intisy.gradle.github.plugin.DependencyResolution;
import io.github.intisy.gradle.github.plugin.DependencyTasks;
import io.github.intisy.gradle.github.plugin.GithubConfigurations;
import io.github.intisy.gradle.github.plugin.PublishTasks;
import io.github.intisy.gradle.github.plugin.ResourceSync;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

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

		GitHubApi api = GitHubApi.create(githubExtension, resourcesExtension, logger);

		ResourceSync.apply(project, logger, resourcesExtension, api.repositories());
		DependencyResolution.apply(project, logger, githubExtension, api.releases());
		DependencyMetadata.apply(project, logger);
		DependencyTasks.apply(project, logger, githubExtension, api.releases());
		PublishTasks.apply(project, logger, publishExtension, api.repositories(), api.publishing());
	}
}
