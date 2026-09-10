package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.capability.Credentials;
import io.github.intisy.gradle.github.plugin.extension.PackageSourceEntry;
import io.github.intisy.gradle.github.plugin.extension.PackagesExtension;
import org.gradle.api.Project;

/**
 * Registers each declared GitHub Packages registry as a Maven repository on the project.
 */
public final class PackagesResolution {

    private PackagesResolution() {
    }

    /**
     * Turns every {@code github { packages { from "owner/repo" } }} entry into an ordinary Maven
     * repository, so the dependencies resolved through it are ordinary Gradle dependencies.
     *
     * @param project the project the repositories are registered on.
     * @param logger receives diagnostic output.
     * @param packagesExtension the extension supplying the declared entries.
     * @param credentials supplies the token the repositories authenticate with.
     * @implNote Registered after evaluation, because the entries are declared by the build script
     * that is still running when the plugin is applied. Repository order still puts these last,
     * which is what a build wants: a dependency available from Maven Central should not cost a
     * request to a private registry first.
     */
    public static void apply(Project project, Logger logger, PackagesExtension packagesExtension,
                             Credentials credentials) {
        project.afterEvaluate(evaluated -> {
            for (PackageSourceEntry entry : packagesExtension.getSources()) {
                register(evaluated, logger, entry, credentials);
            }
        });
    }

    private static void register(Project project, Logger logger, PackageSourceEntry entry, Credentials credentials) {
        if (credentials.apiKey() == null) {
            logger.warn("github.packages declares " + entry + " but no token was resolved, and GitHub Packages "
                    + "refuses an anonymous read even of a public package. Set github { auth { token = \"...\" } }, "
                    + "or GITHUB_TOKEN, or sign in with gh auth login.");
        }
        project.getRepositories().maven(repository -> {
            repository.setName(repositoryName(entry));
            repository.setUrl(project.uri(entry.getUrl()));
            repository.credentials(passwordCredentials -> {
                passwordCredentials.setUsername(username(entry));
                passwordCredentials.setPassword(credentials.apiKey());
            });
            if (entry.getGroup() != null) {
                repository.content(content -> content.includeGroup(entry.getGroup()));
            }
        });
        logger.debug("Resolving packages from " + entry.getUrl()
                + (entry.getGroup() != null ? " for group " + entry.getGroup() : ""));
    }

    /**
     * @param entry the declared entry.
     * @return a repository name unique per entry, since Gradle rejects two repositories sharing one.
     */
    private static String repositoryName(PackageSourceEntry entry) {
        return ("githubPackages_" + entry.getOwner() + "_" + entry.getRepo()).replaceAll("[^A-Za-z0-9_]", "_");
    }

    /**
     * @param entry the declared entry.
     * @return the username sent with the token.
     * @implNote GitHub Packages authenticates on the token alone and ignores this value, but Gradle
     * still requires one, and a recognisable one keeps a 401 readable.
     */
    private static String username(PackageSourceEntry entry) {
        String actor = System.getenv("GITHUB_ACTOR");
        return actor != null && !actor.trim().isEmpty() ? actor.trim() : entry.getOwner();
    }
}
