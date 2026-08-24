package io.github.intisy.gradle.github.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.api.ReleaseNotFoundException;
import io.github.intisy.gradle.github.api.config.ResourceSettings;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.api.model.DeclaredDependency;
import io.github.intisy.gradle.github.api.model.Release;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.impl.github.GitHub;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
     * old null return did, but the skip must now be logged (it was previously silent).
     */
    @Test
    public void missingClassifierArtifactIsSkippedWithAWarning() throws IOException {
        Project project = ProjectBuilder.builder().withName("missing-classifier-test").build();
        project.getPluginManager().apply("java");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0:api");

        GithubExtension githubExtension = new GithubExtension();
        CapturingLogger logger = new CapturingLogger(githubExtension, project);

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
        assertTrue(logger.warnings.stream().anyMatch(w -> w.contains("some-owner:some-repo:1.0.0") && w.contains("api")),
                "the skip must be logged, naming the coordinate and the classifier, instead of vanishing silently");
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

    /**
     * Declares the same underlying jar through all three resolution branches (no-classifier,
     * {@code :all}, and an explicit classifier) in one project evaluation, driving the real
     * {@link DependencyResolution#apply} loop rather than a per-branch stub. Pins that every branch
     * consults {@code addedJars}, and that its key is the configuration-and-jar PAIR: three distinct
     * configurations each asked for the jar, so each gets it once.
     */
    @Test
    public void sameJarAskedForByThreeConfigurationsReachesEachOfThemOnce() throws IOException {
        Project project = ProjectBuilder.builder().withName("dedup-across-branches-test").build();
        project.getPluginManager().apply("java-library");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0");
        project.getDependencies().add("githubApi", "some-owner:some-repo:1.0.0:all");
        project.getDependencies().add("githubCompileOnly", "some-owner:some-repo:1.0.0:extra");

        File sharedJar = File.createTempFile("shared-github-dependency", ".jar");
        sharedJar.deleteOnExit();

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
                return Optional.of(sharedJar);
            }
            public List<File> downloadAllModuleJars(String owner, String repo, String version) {
                return Collections.singletonList(sharedJar);
            }
            public List<File> resolveWithDependencies(String owner, String repo, String version) {
                return Collections.singletonList(sharedJar);
            }
            public List<DeclaredDependency> declaredDependencies(File jar) {
                throw new UnsupportedOperationException();
            }
        });

        ((ProjectInternal) project).evaluate();

        int totalAdded = project.getConfigurations().getByName("implementation").getDependencies().size()
                + project.getConfigurations().getByName("api").getDependencies().size()
                + project.getConfigurations().getByName("compileOnly").getDependencies().size();
        assertEquals(3, totalAdded,
                "each configuration that asked for the jar must receive it, through whichever branch "
                        + "resolved it");
    }

    /**
     * The companion to the test above: within ONE configuration, a jar reached through more than one
     * coordinate is still added once.
     */
    @Test
    public void sameJarAskedForTwiceByOneConfigurationIsAddedOnce() throws IOException {
        Project project = ProjectBuilder.builder().withName("dedup-within-configuration-test").build();
        project.getPluginManager().apply("java-library");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0:one");
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0:two");

        File sharedJar = File.createTempFile("shared-one-configuration", ".jar");
        sharedJar.deleteOnExit();

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
                return Optional.of(sharedJar);
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

        assertEquals(1, project.getConfigurations().getByName("implementation").getDependencies().size(),
                "one configuration must receive the jar once, however many coordinates resolved to it");
    }

    /**
     * An annotation processor resolves like any other jar but must land on {@code
     * annotationProcessor}, the one native configuration a consumer cannot substitute with {@code
     * compileOnly}: a processor merely on the compile classpath is not run by javac.
     */
    @Test
    public void annotationProcessorDependencyLandsOnTheNativeAnnotationProcessorConfiguration() throws IOException {
        Project project = ProjectBuilder.builder().withName("annotation-processor-test").build();
        project.getPluginManager().apply("java");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubAnnotationProcessor", "some-owner:some-repo:1.0.0:processor");

        File processorJar = File.createTempFile("github-annotation-processor", ".jar");
        processorJar.deleteOnExit();

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
                return Optional.of(processorJar);
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

        assertEquals(1, project.getConfigurations().getByName("annotationProcessor").getDependencies().size(),
                "a githubAnnotationProcessor dependency must be added to the native annotationProcessor configuration");
        assertEquals(0, project.getConfigurations().getByName("compileOnly").getDependencies().size(),
                "and must not leak onto the compile classpath");
    }

    /**
     * The two tests above stub {@link Releases} directly, which proves {@link DependencyResolution}
     * absorbed the {@code Optional} contract correctly but never exercises the real {@link GitHub}
     * adapter. These two drive the real adapter (via its overridable, public, non-final
     * {@link GitHub#fetchReleaseByTag}, offline) through the classifier branch for both kinds of
     * absence: a nonexistent release must still fail the build, and a release that exists but lacks
     * the asset must still be skipped (now with the warning from
     * {@link #missingClassifierArtifactIsSkippedWithAWarning}).
     */
    @Test
    public void endToEndClassifierDependencyWithNonexistentReleaseFailsTheBuildThroughTheRealGitHubClient(@TempDir File tempHome) {
        Project project = ProjectBuilder.builder().withName("e2e-missing-release-test").build();
        project.getPluginManager().apply("java");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:9.9.9:api");

        GithubExtension githubExtension = new GithubExtension();
        Logger logger = new Logger(githubExtension, project);
        ReleaseNotFoundException noRelease = new ReleaseNotFoundException(
                "No release found for some-owner/some-repo with tag '9.9.9' or 'v9.9.9'.",
                "some-owner/some-repo:9.9.9", Arrays.asList("9.9.9", "v9.9.9"));

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(logger, null, noRelease);
            DependencyResolution.apply(project, logger, githubExtension, gh);

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> ((ProjectInternal) project).evaluate());
            assertTrue(collectMessages(thrown).contains("No release found for some-owner/some-repo"),
                    "a classifier dependency naming a nonexistent release must still fail the build "
                            + "through the real GitHub client, not just a hand-written stub");
        });
    }

    @Test
    public void endToEndClassifierDependencyWithMissingAssetIsSkippedAndWarnedThroughTheRealGitHubClient(@TempDir File tempHome) {
        Project project = ProjectBuilder.builder().withName("e2e-missing-asset-test").build();
        project.getPluginManager().apply("java");
        GithubConfigurations.apply(project);
        project.getDependencies().add("githubImplementation", "some-owner:some-repo:1.0.0:api");

        GithubExtension githubExtension = new GithubExtension();
        CapturingLogger logger = new CapturingLogger(githubExtension, project);
        JsonObject release = new JsonObject();
        release.add("assets", new JsonArray());

        withTempHome(tempHome, () -> {
            GitHub gh = new FetchStubGitHub(logger, release, null);
            DependencyResolution.apply(project, logger, githubExtension, gh);
            ((ProjectInternal) project).evaluate();

            Set<Dependency> implementationDependencies = project.getConfigurations().getByName("implementation").getDependencies();
            assertEquals(0, implementationDependencies.size(),
                    "an absent classifier asset must still be skipped through the real GitHub client");
            assertTrue(logger.warnings.stream().anyMatch(w -> w.contains("some-owner:some-repo:1.0.0") && w.contains("api")),
                    "the skip must still be logged through the real GitHub client");
        });
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

    private void withTempHome(File tempHome, Runnable body) {
        String original = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.getAbsolutePath());
        try {
            body.run();
        } finally {
            System.setProperty("user.home", original);
        }
    }

    private static final class CapturingLogger extends Logger {
        final List<String> warnings = new ArrayList<String>();

        CapturingLogger(GithubExtension extension, Project project) {
            super(extension, project);
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    }

    /**
     * Overrides the public, non-final {@link GitHub#fetchReleaseByTag} to return canned data (or
     * throw) instead of making a real HTTP call, mirroring the identical seam used in
     * {@code TestDownloadJarOptional}.
     */
    private static final class FetchStubGitHub extends GitHub {
        private final JsonObject canned;
        private final RuntimeException toThrow;

        FetchStubGitHub(GitHubLogger logger, JsonObject canned, RuntimeException toThrow) {
            super(logger, new ResourceSettings(), new GithubExtension());
            this.canned = canned;
            this.toThrow = toThrow;
        }

        @Override
        public JsonObject fetchReleaseByTag(String repoOwner, String repoName, String version) {
            if (toThrow != null) {
                throw toThrow;
            }
            return canned;
        }
    }
}
