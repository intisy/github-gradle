package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.api.model.DeclaredDependency;
import io.github.intisy.gradle.github.api.model.Release;
import io.github.intisy.gradle.github.api.Releases;
import io.github.intisy.gradle.github.extension.GithubExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ProjectBuilder} projects never fire {@code afterEvaluate} on their own, so
 * {@link DependencyResolution#apply} needs the project forced through evaluation to run at all.
 * The {@link Releases} stub below stands in for the real GitHub client so this stays offline: no
 * network call is made.
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

        DependencyResolution.apply(project, logger, githubExtension, new Releases() {
            public String latestVersion(String owner, String repo) {
                throw new UnsupportedOperationException();
            }
            public Release releaseByTag(String owner, String repo, String tag) {
                throw new UnsupportedOperationException();
            }
            public Release latestRelease(String owner, String repo) {
                throw new UnsupportedOperationException();
            }
            public File downloadJar(String owner, String repo, String version) {
                throw new UnsupportedOperationException();
            }
            public File downloadJar(String owner, String repo, String version, String classifier) {
                throw new UnsupportedOperationException();
            }
            public List<File> downloadAllModuleJars(String owner, String repo, String version) {
                throw new UnsupportedOperationException();
            }
            public List<File> resolveWithDependencies(String owner, String repo, String version) {
                return Collections.singletonList(fakeJar);
            }
            public List<DeclaredDependency> declaredDependencies(File jar) {
                throw new UnsupportedOperationException();
            }
        });

        ((ProjectInternal) project).evaluate();

        Set<Dependency> implementationDependencies = project.getConfigurations().getByName("implementation").getDependencies();
        assertEquals(1, implementationDependencies.size(),
                "the githubImplementation dependency should have been resolved into the native "
                        + "'implementation' configuration per GITHUB_TO_GRADLE");
    }
}
