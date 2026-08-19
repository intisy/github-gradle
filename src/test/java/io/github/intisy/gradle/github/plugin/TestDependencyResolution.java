package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.extension.GithubExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ProjectBuilder} projects never fire {@code afterEvaluate} on their own, so
 * {@link DependencyResolution#apply} needs the project forced through evaluation to run at all.
 * The {@link DependencyResolution.DependencyAssetResolver} stub below stands in for the real
 * GitHub client so this stays offline: no network call is made.
 */
public class TestDependencyResolution {

    @Test
    public void githubImplementationDependencyResolvesIntoItsNativeGradleConfiguration() throws IOException {
        Project project = ProjectBuilder.builder().withName("dependency-resolution-test").build();
        project.getPluginManager().apply("java");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0");

        File fakeJar = File.createTempFile("fake-github-dependency", ".jar");
        fakeJar.deleteOnExit();

        GithubExtension githubExtension = new GithubExtension();
        Logger logger = new Logger(githubExtension, project);

        DependencyResolution.apply(project, logger, githubExtension, new DependencyResolution.DependencyAssetResolver() {
            public void getAssetWithTransitives(String repoOwner, String repoName, String version, Set<String> resolved, List<File> collected) {
                collected.add(fakeJar);
            }
            public void getAllModuleAssets(String repoOwner, String repoName, String version, List<File> collected) {
                collected.add(fakeJar);
            }
            public File getAssetWithClassifier(String repoOwner, String repoName, String version, String classifier) {
                return fakeJar;
            }
        });

        ((ProjectInternal) project).evaluate();

        Set<Dependency> implementationDependencies = project.getConfigurations().getByName("implementation").getDependencies();
        assertEquals(1, implementationDependencies.size(),
                "the githubImplementation dependency should have been resolved into the native "
                        + "'implementation' configuration per GITHUB_TO_GRADLE");
    }
}
