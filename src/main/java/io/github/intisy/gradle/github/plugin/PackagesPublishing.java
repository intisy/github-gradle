package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.capability.Credentials;
import io.github.intisy.gradle.github.api.capability.Repositories;
import io.github.intisy.gradle.github.api.model.RemoteRepo;
import io.github.intisy.gradle.github.plugin.extension.PackagesPublishExtension;
import io.github.intisy.gradle.github.plugin.extension.PublishExtension;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

/**
 * Sends the project's Maven publications, and its subprojects', to GitHub Packages as part of
 * {@code publishGithub}.
 */
public final class PackagesPublishing {

    /** The repository this registers, and therefore half of the publish task's name. */
    static final String REPOSITORY_NAME = "githubPackages";

    /** The task Gradle derives from {@link #REPOSITORY_NAME}. */
    static final String PUBLISH_TASK_NAME = "publishAllPublicationsToGithubPackagesRepository";

    private PackagesPublishing() {
    }

    /**
     * Registers the GitHub Packages repository on every project of this build that publishes
     * something, and makes {@code publishGithub} publish to each, when
     * {@code publishGithub.packages.enabled} is set.
     *
     * @param project the project the block was written in, whose {@code publishGithub} does the work.
     * @param logger receives diagnostic output.
     * @param publishExtension the extension supplying the destination and the owner/repo fallback.
     * @param repositories the client used to read the git remote when the owner/repo is not stated.
     * @param credentials supplies the token the repository authenticates with.
     * @implNote Subprojects are covered because a root that publishes nothing of its own is the
     * normal shape of a multi-module repository, and the release-asset half already spans them
     * through {@code artifact { modules = true }}. The destination is registered through
     * {@code withPlugin("maven-publish")} rather than by applying that plugin: whether the
     * destination is enabled is only known after the build script has run, and applying
     * {@code maven-publish} from inside {@code afterEvaluate} is the one point at which it can no
     * longer add its own publish tasks.
     */
    public static void apply(Project project, Logger logger, PublishExtension publishExtension,
                             Repositories repositories, Credentials credentials) {
        Registered registered = new Registered();
        registerWhenPublishing(project, project, logger, publishExtension, repositories, credentials, registered);
        project.subprojects(subproject ->
                registerWhenPublishing(project, subproject, logger, publishExtension, repositories, credentials, registered));
        project.afterEvaluate(evaluated -> guard(evaluated, publishExtension, registered));
    }

    /**
     * Registers {@code target} as a destination once it turns out to apply {@code maven-publish}.
     *
     * @param owningProject the project whose {@code publishGithub} publishes it.
     * @param target the project whose publications are published.
     * @param logger receives diagnostic output.
     * @param publishExtension the extension supplying the destination and the owner/repo fallback.
     * @param repositories the client used to read the git remote.
     * @param credentials supplies the token.
     * @param registered counts the destinations, for the guard below.
     */
    private static void registerWhenPublishing(Project owningProject, Project target, Logger logger,
                                               PublishExtension publishExtension, Repositories repositories,
                                               Credentials credentials, Registered registered) {
        target.getPluginManager().withPlugin("maven-publish", applied -> target.afterEvaluate(evaluated -> {
            if (!publishExtension.getPackages().isEnabled()) {
                return;
            }
            register(owningProject, evaluated, logger, publishExtension, repositories, credentials);
            registered.count++;
        }));
    }

    private static void register(Project owningProject, Project target, Logger logger,
                                 PublishExtension publishExtension, Repositories repositories,
                                 Credentials credentials) {
        RemoteRepo destination = destination(owningProject, publishExtension, repositories);
        String url = "https://maven.pkg.github.com/" + destination.getOwner() + "/" + destination.getRepo();
        PublishingExtension publishing = target.getExtensions().getByType(PublishingExtension.class);

        publishing.getRepositories().maven(repository -> {
            repository.setName(REPOSITORY_NAME);
            repository.setUrl(target.uri(url));
            repository.credentials(passwordCredentials -> {
                passwordCredentials.setUsername(username(destination));
                passwordCredentials.setPassword(credentials.apiKey());
            });
        });
        logger.debug("GitHub Packages destination for " + target.getPath() + ": " + url);

        if (publishing.getPublications().isEmpty() && target.getPlugins().hasPlugin("java")) {
            publishing.getPublications().create(REPOSITORY_NAME, MavenPublication.class, publication ->
                    publication.from(target.getComponents().getByName("java")));
            logger.debug(target.getPath() + " declared no publication, so one was created from the java component.");
        }

        target.getTasks().named(PUBLISH_TASK_NAME).configure(publish -> publish.doFirst(task -> {
            if (credentials.apiKey() == null) {
                throw new IllegalStateException("Publishing to GitHub Packages needs a token with write:packages. "
                        + "Set github { auth { token = \"...\" } }, or GITHUB_TOKEN, or sign in with "
                        + "gh auth login (and gh auth refresh -s write:packages).");
            }
        }));
        owningProject.getTasks().named("publishGithub").configure(publishGithub ->
                publishGithub.dependsOn(target.getTasks().named(PUBLISH_TASK_NAME)));
    }

    /**
     * Reports a build that would publish no package at all.
     *
     * @param project the project the block was written in.
     * @param publishExtension the extension stating whether the destination is enabled.
     * @param registered the destination count.
     * @implNote The single-project case fails at once, because it is the common misconfiguration and
     * the answer is already known. A project with subprojects cannot be judged that early, since
     * they are evaluated after it is, so that case is checked when {@code publishGithub} runs, by
     * which time the count is final.
     */
    private static void guard(Project project, PublishExtension publishExtension, Registered registered) {
        if (!publishExtension.getPackages().isEnabled()) {
            return;
        }
        if (project.getSubprojects().isEmpty() && !project.getPlugins().hasPlugin("maven-publish")) {
            throw new IllegalStateException("publishGithub.packages is enabled but this project does not apply "
                    + "maven-publish, so it has no publication to send. Add id 'maven-publish' to its plugins block.");
        }
        project.getTasks().named("publishGithub").configure(publishGithub -> publishGithub.doFirst(task -> {
            if (registered.count == 0) {
                throw new IllegalStateException("publishGithub.packages is enabled but no project in this build "
                        + "applies maven-publish, so nothing would reach GitHub Packages.");
            }
        }));
    }

    /**
     * @param project the project being published.
     * @param publishExtension the extension supplying the owner/repo fallback.
     * @param repositories the client used to read the git remote.
     * @return the repository the packages are published under.
     */
    private static RemoteRepo destination(Project project, PublishExtension publishExtension, Repositories repositories) {
        PackagesPublishExtension packages = publishExtension.getPackages();
        if (packages.getOwner() != null && packages.getRepo() != null) {
            return new RemoteRepo(packages.getOwner(), packages.getRepo());
        }
        RemoteRepo resolved = PublishTarget.resolve(project, publishExtension, repositories);
        return new RemoteRepo(
                packages.getOwner() != null ? packages.getOwner() : resolved.getOwner(),
                packages.getRepo() != null ? packages.getRepo() : resolved.getRepo());
    }

    /**
     * @param destination the repository being published to.
     * @return the username sent with the token.
     * @implNote GitHub Packages authenticates on the token alone and ignores this value, but Gradle
     * still requires one, and a recognisable one keeps a 401 readable.
     */
    private static String username(RemoteRepo destination) {
        String actor = System.getenv("GITHUB_ACTOR");
        return actor != null && !actor.trim().isEmpty() ? actor.trim() : destination.getOwner();
    }

    /** How many destinations were registered, which only the last subproject's evaluation settles. */
    private static final class Registered {
        private int count;
    }
}
