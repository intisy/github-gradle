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
 * Sends the project's Maven publications to GitHub Packages as part of {@code publishGithub}.
 */
public final class PackagesPublishing {

    /** The repository this registers, and therefore half of the publish task's name. */
    static final String REPOSITORY_NAME = "githubPackages";

    /** The task Gradle derives from {@link #REPOSITORY_NAME}. */
    static final String PUBLISH_TASK_NAME = "publishAllPublicationsToGithubPackagesRepository";

    private PackagesPublishing() {
    }

    /**
     * Registers the GitHub Packages repository and makes {@code publishGithub} publish to it, when
     * {@code publishGithub.packages.enabled} is set.
     *
     * @param project the project whose publications are published.
     * @param logger receives diagnostic output.
     * @param publishExtension the extension supplying the destination and the owner/repo fallback.
     * @param repositories the client used to read the git remote when the owner/repo is not stated.
     * @param credentials supplies the token the repository authenticates with.
     * @implNote The repository is registered through {@code withPlugin("maven-publish")} rather
     * than by applying that plugin here. Whether the destination is enabled is only known after
     * the build script has run, and applying a plugin from inside {@code afterEvaluate} is the one
     * point at which {@code maven-publish} can no longer add its own publish tasks. Every project
     * that has something to publish already applies it, so requiring it costs nothing and an
     * absent one is reported rather than worked around.
     */
    public static void apply(Project project, Logger logger, PublishExtension publishExtension,
                             Repositories repositories, Credentials credentials) {
        project.getPluginManager().withPlugin("maven-publish", applied ->
                project.afterEvaluate(evaluated -> register(evaluated, logger, publishExtension, repositories, credentials)));
        project.afterEvaluate(evaluated -> {
            if (publishExtension.getPackages().isEnabled() && !evaluated.getPlugins().hasPlugin("maven-publish")) {
                throw new IllegalStateException("publishGithub.packages is enabled but this project does not apply "
                        + "maven-publish, so it has no publication to send. Add id 'maven-publish' to its plugins block.");
            }
        });
    }

    private static void register(Project project, Logger logger, PublishExtension publishExtension,
                                 Repositories repositories, Credentials credentials) {
        PackagesPublishExtension packages = publishExtension.getPackages();
        if (!packages.isEnabled()) {
            return;
        }

        RemoteRepo target = target(project, publishExtension, repositories);
        String url = "https://maven.pkg.github.com/" + target.getOwner() + "/" + target.getRepo();
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

        publishing.getRepositories().maven(repository -> {
            repository.setName(REPOSITORY_NAME);
            repository.setUrl(project.uri(url));
            repository.credentials(passwordCredentials -> {
                passwordCredentials.setUsername(username(target));
                passwordCredentials.setPassword(credentials.apiKey());
            });
        });
        logger.debug("GitHub Packages destination: " + url);

        if (publishing.getPublications().isEmpty() && project.getPlugins().hasPlugin("java")) {
            publishing.getPublications().create(REPOSITORY_NAME, MavenPublication.class, publication ->
                    publication.from(project.getComponents().getByName("java")));
            logger.debug("No publication was declared, so one was created from the java component.");
        }

        project.getTasks().named(PUBLISH_TASK_NAME).configure(publish -> publish.doFirst(task -> {
            if (credentials.apiKey() == null) {
                throw new IllegalStateException("Publishing to GitHub Packages needs a token with write:packages. "
                        + "Set github { auth { token = \"...\" } }, or GITHUB_TOKEN, or sign in with "
                        + "gh auth login (and gh auth refresh -s write:packages).");
            }
        }));
        project.getTasks().named("publishGithub").configure(publishGithub -> publishGithub.dependsOn(PUBLISH_TASK_NAME));
    }

    /**
     * @param project the project being published.
     * @param publishExtension the extension supplying the owner/repo fallback.
     * @param repositories the client used to read the git remote.
     * @return the repository the packages are published under.
     */
    private static RemoteRepo target(Project project, PublishExtension publishExtension, Repositories repositories) {
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
     * @param target the repository being published to.
     * @return the username sent with the token.
     * @implNote GitHub Packages authenticates on the token alone and ignores this value, but Gradle
     * still requires one, and a recognisable one keeps a 401 readable.
     */
    private static String username(RemoteRepo target) {
        String actor = System.getenv("GITHUB_ACTOR");
        return actor != null && !actor.trim().isEmpty() ? actor.trim() : target.getOwner();
    }
}
