package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.extension.GithubExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves each GitHub dependency configuration's declared dependencies into local JAR files.
 */
public class DependencyResolution {

	/**
	 * The subset of the GitHub client this class needs, kept free of {@code impl} types so the
	 * layering rule (only {@code api}/{@code impl} may name {@code impl}) still holds.
	 */
	public interface DependencyAssetResolver {
		void getAssetWithTransitives(String repoOwner, String repoName, String version, Set<String> resolved, List<File> collected);
		void getAllModuleAssets(String repoOwner, String repoName, String version, List<File> collected);
		File getAssetWithClassifier(String repoOwner, String repoName, String version, String classifier);
	}

	public static void apply(Project project, Logger logger, GithubExtension githubExtension, DependencyAssetResolver gitHub) {
		project.afterEvaluate(proj -> {
			Set<String> resolved = new HashSet<String>();
			List<File> allJars = new ArrayList<File>();
			for (String cfgName : GithubConfigurations.GITHUB_CONFIGS) {
				String nativeCfg = GithubConfigurations.GITHUB_TO_GRADLE.get(cfgName);
				boolean needsJavaLibrary = GithubConfigurations.JAVA_LIBRARY_CONFIGS.contains(nativeCfg);
				if (needsJavaLibrary && !proj.getPlugins().hasPlugin("java-library")) {
					continue;
				}
				Configuration cfg = proj.getConfigurations().getByName(cfgName);
				for (Dependency dependency : cfg.getDependencies()) {
					try {
						String classifier = GithubConfigurations.extractClassifier(dependency);
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
	}
}
