package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.model.DeclaredDependency;
import io.github.intisy.gradle.github.api.model.Release;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            public Optional<File> downloadJar(String owner, String repo, String version) {
                throw new UnsupportedOperationException();
            }
            public Optional<File> downloadJar(String owner, String repo, String version, String classifier) {
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

    /**
     * Pins the classifier branch's absence handling: {@link Releases#downloadJar(String, String, String, String)}
     * returning an empty {@code Optional} must be skipped without failing the build, exactly like the
     * old null return did.
     */
    @Test
    public void missingClassifierArtifactIsSkippedSilently() throws IOException {
        Project project = ProjectBuilder.builder().withName("missing-classifier-test").build();
        project.getPluginManager().apply("java");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0:api");

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
            public Optional<File> downloadJar(String owner, String repo, String version) {
                throw new UnsupportedOperationException();
            }
            public Optional<File> downloadJar(String owner, String repo, String version, String classifier) {
                return Optional.empty();
            }
            public List<File> downloadAllModuleJars(String owner, String repo, String version) {
                throw new UnsupportedOperationException();
            }
            public List<File> resolveWithDependencies(String owner, String repo, String version) {
                throw new UnsupportedOperationException();
            }
            public List<DeclaredDependency> declaredDependencies(File jar) {
                throw new UnsupportedOperationException();
            }
        });

        ((ProjectInternal) project).evaluate();

        Set<Dependency> implementationDependencies = project.getConfigurations().getByName("implementation").getDependencies();
        assertEquals(0, implementationDependencies.size(),
                "an absent classifier artifact must be skipped, not added and not fail the build");
    }

    /**
     * Pins the no-classifier branch's failure handling: a jar that cannot be resolved still fails
     * the build (via {@link Releases#resolveWithDependencies}, which throws unchecked for a missing
     * root jar exactly as before this change; the classifier branch is the only one whose contract
     * moved to {@link Optional}).
     */
    @Test
    public void missingMainJarStillFailsTheBuild() {
        Project project = ProjectBuilder.builder().withName("missing-main-jar-test").build();
        project.getPluginManager().apply("java");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0");

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
            public Optional<File> downloadJar(String owner, String repo, String version) {
                throw new UnsupportedOperationException();
            }
            public Optional<File> downloadJar(String owner, String repo, String version, String classifier) {
                throw new UnsupportedOperationException();
            }
            public List<File> downloadAllModuleJars(String owner, String repo, String version) {
                throw new UnsupportedOperationException();
            }
            public List<File> resolveWithDependencies(String owner, String repo, String version) {
                throw new RuntimeException("No release jar found for " + owner + ":" + repo + ":" + version);
            }
            public List<DeclaredDependency> declaredDependencies(File jar) {
                throw new UnsupportedOperationException();
            }
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> ((ProjectInternal) project).evaluate());
        assertTrue(collectMessages(thrown).contains("No release jar found for some-owner:some-repo:1.0.0"),
                "the missing-main-jar failure should propagate and fail the build");
    }

    private static String collectMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append('\n');
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return sb.toString();
    }
}
