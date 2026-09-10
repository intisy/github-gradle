package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.capability.Credentials;
import io.github.intisy.gradle.github.api.capability.Repositories;
import io.github.intisy.gradle.github.api.model.RemoteRepo;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.plugin.extension.PublishExtension;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code publishGithub} sends the project's publications to GitHub Packages as well as attaching
 * jars to the release, so one task reaches both destinations and neither can drift behind the
 * other.
 */
public class TestPackagesPublishing {

    @Test
    public void theDestinationIsRegisteredAtThePackagesUrlForTheResolvedRepository() {
        Project project = publishingProject(true);

        MavenArtifactRepository repository = packagesRepository(project);
        assertNotNull(repository, "the githubPackages repository should be registered");
        assertEquals("https://maven.pkg.github.com/my-org/my-repo", repository.getUrl().toString());
    }

    @Test
    public void theDestinationCarriesTheResolvedToken() {
        Project project = publishingProject(true);

        PasswordCredentials credentials = packagesRepository(project).getCredentials(PasswordCredentials.class);
        assertEquals("a-token", credentials.getPassword());
        assertNotNull(credentials.getUsername(), "Gradle requires a username even though GitHub ignores it");
    }

    @Test
    public void nothingIsRegisteredWhileTheDestinationIsDisabled() {
        Project project = publishingProject(false);

        assertEquals(null, packagesRepository(project),
                "a disabled destination must leave the publishing repositories untouched");
    }

    @Test
    public void publishGithubPublishesToTheDestination() {
        Project project = publishingProject(true);

        Task publishGithub = project.getTasks().getByName("publishGithub");
        assertTrue(dependencyNames(project, publishGithub).contains(PackagesPublishing.PUBLISH_TASK_NAME),
                "publishGithub must publish the packages too, got " + dependencyNames(project, publishGithub));
    }

    /**
     * A project with the java plugin and no publication of its own would otherwise publish an empty
     * set, succeeding while shipping nothing.
     */
    @Test
    public void aProjectWithNoPublicationGetsOneFromTheJavaComponent() {
        Project project = publishingProject(true);

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        assertEquals(1, publishing.getPublications().size());
        assertNotNull(publishing.getPublications().findByName("githubPackages"));
    }

    @Test
    public void aDeclaredPublicationIsNotDuplicated() {
        Project project = newProject();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("maven-publish");
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        publishing.getPublications().create("mine", org.gradle.api.publish.maven.MavenPublication.class);

        applyWith(project, true);
        evaluate(project);

        assertEquals(1, publishing.getPublications().size(), "the declared publication is the one that is published");
        assertNotNull(publishing.getPublications().findByName("mine"));
    }

    /**
     * Enabling the destination without {@code maven-publish} would otherwise register nothing and
     * leave {@code publishGithub} quietly publishing only the release asset.
     */
    @Test
    public void enablingTheDestinationWithoutMavenPublishIsRefused() {
        Project project = newProject();
        project.getPluginManager().apply("java");
        applyWith(project, true);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> evaluate(project));

        assertTrue(rootCauseMessage(failure).contains("maven-publish"),
                "the failure should name the plugin to add, got " + rootCauseMessage(failure));
    }

    @Test
    public void aProjectWithoutMavenPublishIsUntouchedWhileTheDestinationIsDisabled() {
        Project project = newProject();
        project.getPluginManager().apply("java");
        applyWith(project, false);

        evaluate(project);
    }

    /**
     * Five of the nine spisor repositories are multi-module, and a root that publishes nothing of
     * its own is the normal shape for one. Registering only on the project the block was written in
     * would leave every such repository publishing no package at all.
     */
    @Test
    public void aSubprojectPublicationReachesTheDestination() {
        Project root = newProject();
        Project module = ProjectBuilder.builder().withName("common").withParent(root).build();
        module.getPluginManager().apply("java");
        module.getPluginManager().apply("maven-publish");

        applyWith(root, true);
        evaluate(root);
        evaluate(module);

        MavenArtifactRepository repository = packagesRepository(module);
        assertNotNull(repository, "the subproject should carry the destination");
        assertEquals("https://maven.pkg.github.com/my-org/my-repo", repository.getUrl().toString());
    }

    @Test
    public void theRootsPublishGithubPublishesEverySubproject() {
        Project root = newProject();
        Project first = ProjectBuilder.builder().withName("common").withParent(root).build();
        Project second = ProjectBuilder.builder().withName("objectstore").withParent(root).build();
        for (Project module : new Project[]{first, second}) {
            module.getPluginManager().apply("java");
            module.getPluginManager().apply("maven-publish");
        }

        applyWith(root, true);
        evaluate(root);
        evaluate(first);
        evaluate(second);

        List<String> dependencies = dependencyNames(root, root.getTasks().getByName("publishGithub"));
        assertEquals(2, countPackagesPublishes(dependencies),
                "publishGithub should publish both modules' packages, got " + dependencies);
    }

    /**
     * api-host and storage-host both publish from the root and from subprojects, so the two cases
     * are not alternatives.
     */
    @Test
    public void aRootThatAlsoPublishesCoversItselfAndItsSubprojects() {
        Project root = newProject();
        root.getPluginManager().apply("java");
        root.getPluginManager().apply("maven-publish");
        Project module = ProjectBuilder.builder().withName("kit").withParent(root).build();
        module.getPluginManager().apply("java");
        module.getPluginManager().apply("maven-publish");

        applyWith(root, true);
        evaluate(root);
        evaluate(module);

        assertNotNull(packagesRepository(root), "the root publishes too");
        assertNotNull(packagesRepository(module), "and so does the subproject");
    }

    @Test
    public void aSubprojectWithoutMavenPublishIsSkippedRatherThanFailing() {
        Project root = newProject();
        root.getPluginManager().apply("java");
        root.getPluginManager().apply("maven-publish");
        Project consumer = ProjectBuilder.builder().withName("app").withParent(root).build();
        consumer.getPluginManager().apply("java");

        applyWith(root, true);
        evaluate(root);
        evaluate(consumer);

        assertNotNull(packagesRepository(root));
        assertEquals(null, packagesRepository(consumer), "a project with nothing to publish is not a destination");
    }

    /**
     * A root with subprojects cannot be judged while it is being evaluated, since they are evaluated
     * after it is, so the case where nothing in the build publishes anything is caught when
     * publishGithub runs. Without it the task succeeds having sent no package anywhere.
     */
    @Test
    public void aBuildWhereNothingPublishesIsRefusedWhenPublishGithubRuns() {
        Project root = newProject();
        root.getPluginManager().apply("java");
        Project module = ProjectBuilder.builder().withName("app").withParent(root).build();
        module.getPluginManager().apply("java");

        applyWith(root, true);
        evaluate(root);
        evaluate(module);

        Task publishGithub = root.getTasks().getByName("publishGithub");
        RuntimeException failure = assertThrows(RuntimeException.class, () -> runActions(publishGithub));

        assertTrue(rootCauseMessage(failure).contains("GitHub Packages"),
                "the failure should say nothing would reach the registry, got " + rootCauseMessage(failure));
    }

    private void runActions(Task task) {
        for (org.gradle.api.Action<? super Task> action : task.getActions()) {
            action.execute(task);
        }
    }

    private int countPackagesPublishes(List<String> dependencies) {
        int found = 0;
        for (String dependency : dependencies) {
            if (dependency.contains(PackagesPublishing.PUBLISH_TASK_NAME)) {
                found++;
            }
        }
        return found;
    }

    private Project publishingProject(boolean enabled) {
        Project project = newProject();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("maven-publish");
        applyWith(project, enabled);
        evaluate(project);
        return project;
    }

    private Project newProject() {
        return ProjectBuilder.builder().withName("my-repo").build();
    }

    private void applyWith(Project project, boolean enabled) {
        PublishExtension publishExtension = new PublishExtension();
        publishExtension.getPackages().setEnabled(enabled);
        project.getTasks().register("publishGithub");
        PackagesPublishing.apply(project, new Logger(new GithubExtension()), publishExtension,
                new FixedRemote("my-org", "my-repo"), new FixedToken("a-token"));
    }

    /**
     * @implNote {@code ProjectBuilder} never evaluates, so {@code afterEvaluate} actions have to be
     * fired by hand for anything registered inside one to exist.
     */
    private void evaluate(Project project) {
        ((ProjectInternal) project).evaluate();
    }

    private MavenArtifactRepository packagesRepository(Project project) {
        if (!project.getPlugins().hasPlugin("maven-publish")) {
            return null;
        }
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        for (ArtifactRepository repository : publishing.getRepositories()) {
            if (PackagesPublishing.REPOSITORY_NAME.equals(repository.getName())) {
                return (MavenArtifactRepository) repository;
            }
        }
        return null;
    }

    /**
     * @implNote Resolved dependencies only. Reading {@code getDependsOn()} as well counts every
     * dependency twice, once as the declared provider and once as the task it resolves to.
     */
    private List<String> dependencyNames(Project project, Task task) {
        List<String> names = new ArrayList<String>();
        for (Object dependency : task.getTaskDependencies().getDependencies(task)) {
            names.add(((Task) dependency).getName());
        }
        return names;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        StringBuilder messages = new StringBuilder();
        while (cause != null) {
            messages.append(cause.getMessage()).append(" | ");
            cause = cause.getCause();
        }
        return messages.toString();
    }

    private static final class FixedRemote implements Repositories {
        private final RemoteRepo remote;

        FixedRemote(String owner, String repo) {
            this.remote = new RemoteRepo(owner, repo);
        }

        @Override
        public RemoteRepo remoteOf(File projectDir) {
            return remote;
        }

        @Override
        public RemoteRepo configuredRepo() {
            return remote;
        }

        @Override
        public void cloneOrPull(File target, String owner, String repo, String branch) {
        }

        @Override
        public boolean exists(File path) {
            return false;
        }

        @Override
        public boolean isUpToDate(File path) {
            return false;
        }

        @Override
        public void cloneOrPullFrom(File target, String cloneUrl, String branch) {
        }
    }

    private static final class FixedToken implements Credentials {
        private final String token;

        FixedToken(String token) {
            this.token = token;
        }

        @Override
        public String apiKey() {
            return token;
        }

        @Override
        public String sshKey() {
            return null;
        }
    }
}
