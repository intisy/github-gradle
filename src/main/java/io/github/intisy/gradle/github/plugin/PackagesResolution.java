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
     * @param project the project the entries are declared in.
     * @param logger receives diagnostic output.
     * @param packagesExtension the extension supplying the declared entries.
     * @param credentials supplies the token the repositories authenticate with.
     * @implNote Registered on the subprojects as well, because a multi-module build declares its
     * sources once at the root while the modules are what resolve dependencies. Registration
     * happens after each project is evaluated, since the entries are declared by a build script
     * that is still running when the plugin is applied; repository order therefore puts these last,
     * which is what a build wants, as a dependency available from Maven Central should not cost a
     * request to a private registry first.
     */
    public static void apply(Project project, Logger logger, PackagesExtension packagesExtension,
                             Credentials credentials) {
        registerAfterEvaluation(project, logger, packagesExtension, credentials);
        project.subprojects(subproject ->
                registerAfterEvaluation(subproject, logger, packagesExtension, credentials));
        project.afterEvaluate(evaluated -> warnAboutAMissingToken(logger, packagesExtension, credentials));
    }

    private static void registerAfterEvaluation(Project project, Logger logger,
                                                PackagesExtension packagesExtension, Credentials credentials) {
        project.afterEvaluate(evaluated -> {
            for (PackageSourceEntry entry : packagesExtension.getSources()) {
                register(evaluated, logger, entry, credentials);
            }
        });
    }

    /**
     * @param logger receives the warning.
     * @param packagesExtension the extension supplying the declared entries.
     * @param credentials supplies the token.
     * @implNote Warns once for the whole build rather than once per project. The registration below
     * runs for the root and every subproject, so warning there would repeat the same message once
     * per module and bury it.
     */
    private static void warnAboutAMissingToken(Logger logger, PackagesExtension packagesExtension,
                                               Credentials credentials) {
        if (packagesExtension.getSources().isEmpty() || credentials.apiKey() != null) {
            return;
        }
        logger.warn("github.packages declares " + packagesExtension.getSources()
                + " but no token was resolved, and GitHub Packages refuses an anonymous read even of a public "
                + "package. Set github { auth { token = \"...\" } }, or GITHUB_TOKEN, or sign in with gh auth login.");
    }

    private static void register(Project project, Logger logger, PackageSourceEntry entry, Credentials credentials) {
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
