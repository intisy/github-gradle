package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
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
	 * Resolves every {@code githubImplementation}/{@code githubApi}/etc. dependency into a local
	 * jar and adds it to the matching native Gradle configuration, after the project is evaluated.
	 *
	 * @param project the project whose GitHub dependency configurations are resolved.
	 * @param logger receives diagnostic output.
	 * @param githubExtension the extension supplying {@code resilience.skipOnRateLimit}.
	 * @param releases the client used to resolve each dependency to a jar.
	 * @implNote {@link Releases#resolveWithDependencies} deduplicates cycles only within a single
	 * call. {@code addedJars} extends that dedup across every configuration and every dependency
	 * resolved through the no-classifier branch (the {@link Releases#resolveWithDependencies} path):
	 * each distinct jar reached that way is added to the classpath at most once across the whole
	 * configuration loop. The {@code :all} branch ({@link Releases#downloadAllModuleJars}) and the
	 * explicit-classifier branch ({@link Releases#downloadJar(String, String, String, String)}) do
	 * not consult {@code addedJars}, so a jar reachable through more than one of the three branches
	 * is not deduplicated against jars added by the other branches.
	 */
	public static void apply(Project project, Logger logger, GithubExtension githubExtension, Releases releases) {
		project.afterEvaluate(proj -> {
			Set<File> addedJars = new HashSet<File>();
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
							for (File jar : releases.resolveWithDependencies(dependency.getGroup(), dependency.getName(), dependency.getVersion())) {
								if (addedJars.add(jar)) {
									jars.add(jar);
								}
							}
						} else if (classifier.equals("all")) {
							jars.addAll(releases.downloadAllModuleJars(dependency.getGroup(), dependency.getName(), dependency.getVersion()));
						} else {
							File jar = releases.downloadJar(dependency.getGroup(), dependency.getName(), dependency.getVersion(), classifier);
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
