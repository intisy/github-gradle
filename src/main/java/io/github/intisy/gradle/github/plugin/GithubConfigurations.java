package io.github.intisy.gradle.github.plugin;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalDependency;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers the plugin's GitHub dependency configurations.
 */
public class GithubConfigurations {

	/** Names of all GitHub dependency configurations created by this plugin, in declaration order. */
	static final List<String> GITHUB_CONFIGS = Collections.unmodifiableList(Arrays.asList(
		"githubImplementation",
		"githubApi",
		"githubCompileOnly",
		"githubCompileOnlyApi",
		"githubRuntimeOnly",
		"githubAnnotationProcessor",
		"githubTestImplementation",
		"githubTestCompileOnly",
		"githubTestRuntimeOnly",
		"githubTestAnnotationProcessor"
	));

	/**
	 * Maps each GitHub configuration to the native Gradle configuration it feeds. The test-source
	 * entries mirror the main-source ones, because a published jar is as often a test-only
	 * dependency (a shared fixture) as a compile one.
	 * {@code api} and {@code compileOnlyApi} require the {@code java-library} plugin.
	 */
	static final Map<String, String> GITHUB_TO_GRADLE;
	static {
		Map<String, String> m = new HashMap<String, String>();
		m.put("githubImplementation",  "implementation");
		m.put("githubApi",             "api");
		m.put("githubCompileOnly",     "compileOnly");
		m.put("githubCompileOnlyApi",  "compileOnlyApi");
		m.put("githubRuntimeOnly",     "runtimeOnly");
		m.put("githubAnnotationProcessor", "annotationProcessor");
		m.put("githubTestImplementation", "testImplementation");
		m.put("githubTestCompileOnly", "testCompileOnly");
		m.put("githubTestRuntimeOnly", "testRuntimeOnly");
		m.put("githubTestAnnotationProcessor", "testAnnotationProcessor");
		GITHUB_TO_GRADLE = Collections.unmodifiableMap(m);
	}

	/** Gradle configurations that require the {@code java-library} plugin. */
	static final Set<String> JAVA_LIBRARY_CONFIGS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
		"api", "compileOnlyApi"
	)));

	/**
	 * Creates each configuration named in {@link #GITHUB_CONFIGS} on {@code project}, so
	 * {@code githubImplementation}/{@code githubApi}/etc. dependency declarations resolve.
	 *
	 * @param project the project to create the configurations on.
	 */
	public static void apply(Project project) {
		for (String cfgName : GITHUB_CONFIGS) {
			project.getConfigurations().create(cfgName);
		}
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
	 */	static String extractClassifier(Dependency dependency) {
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
	static Set<Dependency> getAllDependencies(Project project) {
		return project.getAllprojects().stream().flatMap(p -> getDependencies(p).stream()).collect(Collectors.toSet());
	}

	/**
	 * @param project the project
	 * @return all github dependency configurations' dependencies for this project only
	 */
	static Set<Dependency> getDependencies(Project project) {
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
