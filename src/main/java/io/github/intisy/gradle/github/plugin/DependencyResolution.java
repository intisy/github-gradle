package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.RateLimitException;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves each GitHub dependency configuration's declared dependencies into local JAR files.
 */
public class DependencyResolution {

	/**
	 * Same as {@link #apply(Project, Logger, GithubExtension, Releases, AddedJars)}, with a fresh,
	 * private dedup set: nothing outside the GitHub branches shares it.
	 *
	 * @param project the project whose GitHub dependency configurations are resolved.
	 * @param logger receives diagnostic output.
	 * @param githubExtension the extension supplying {@code resilience.skipOnRateLimit}.
	 * @param releases the client used to resolve each dependency to a jar.
	 */
	public static void apply(Project project, Logger logger, GithubExtension githubExtension, Releases releases) {
		apply(project, logger, githubExtension, releases, new AddedJars());
	}

	/**
	 * Resolves every {@code githubImplementation}/{@code githubApi}/etc. dependency into a local
	 * jar and adds it to the matching native Gradle configuration, after the project is evaluated.
	 *
	 * @param project the project whose GitHub dependency configurations are resolved.
	 * @param logger receives diagnostic output.
	 * @param githubExtension the extension supplying {@code resilience.skipOnRateLimit}.
	 * @param releases the client used to resolve each dependency to a jar.
	 * @param addedJars the dedup filter, keyed by native configuration and resolved jar; pass the same one given to
	 * {@link SourcesResolution#apply} so a jar reachable from both a {@code github*} coordinate and
	 * a {@code sources { }} entry is added to a native configuration only once.
	 * @implNote {@link Releases#resolveWithDependencies} deduplicates cycles only within a single
	 * call. {@code addedJars} extends that dedup across every configuration, every branch, and
	 * every {@code sources { }} entry: the no-classifier branch
	 * ({@link Releases#resolveWithDependencies}), the {@code :all} branch
	 * ({@link Releases#downloadAllModuleJars}), the explicit-classifier branch
	 * ({@link Releases#downloadJar(String, String, String, String)}), and {@link SourcesResolution}
	 * all consult it, keyed by the native configuration and the resolved file. The guarantee is only as strong as the
	 * cache-naming scheme behind that {@link File}: both the release cache and the source-build
	 * cache key a jar by unescaped {@code "-"}-joined string concatenation (owner, repo, version or
	 * commit), so two logically distinct artifacts could in principle collide on the same path and
	 * be treated as one jar. The url-download cache sidesteps this by keying on a hash of the URL
	 * instead.
	 */
	public static void apply(Project project, Logger logger, GithubExtension githubExtension, Releases releases, AddedJars addedJars) {
		project.afterEvaluate(proj -> {
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
								if (addedJars.add(nativeCfg, jar)) {
									jars.add(jar);
								}
							}
						} else if (classifier.equals("all")) {
							for (File jar : releases.downloadAllModuleJars(dependency.getGroup(), dependency.getName(), dependency.getVersion())) {
								if (addedJars.add(nativeCfg, jar)) {
									jars.add(jar);
								}
							}
						} else {
							Optional<File> jar = releases.downloadJar(dependency.getGroup(), dependency.getName(), dependency.getVersion(), classifier);
							if (jar.isPresent()) {
								if (addedJars.add(nativeCfg, jar.get())) {
									jars.add(jar.get());
								}
							} else {
								logger.warn("No '" + classifier + "' classifier asset found for " + dependency.getGroup()
									+ ":" + dependency.getName() + ":" + dependency.getVersion() + "; skipping it. "
									+ "The compile classpath may be incomplete.");
							}
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
