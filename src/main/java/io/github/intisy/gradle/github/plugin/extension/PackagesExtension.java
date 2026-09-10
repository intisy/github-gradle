package io.github.intisy.gradle.github.plugin.extension;

import groovy.lang.Closure;
import org.gradle.api.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Declares the GitHub Packages repositories a build resolves dependencies from.
 *
 * <pre>
 * github {
 *     packages {
 *         from "my-org/libs"
 *         from("my-org/core") { group = "com.example" }
 *     }
 * }
 *
 * dependencies {
 *     implementation "com.example:common:1.0.0"
 *     testImplementation testFixtures("com.example:common:1.0.0")
 * }
 * </pre>
 *
 * <p>{@code from} is repeatable, and each entry becomes an ordinary Maven repository, so
 * dependencies resolved through it are ordinary Gradle dependencies with full metadata.
 *
 * @apiNote Deliberately no new coordinate form. A {@code githubImplementation "OWNER:REPO:TAG"}
 * triple names a repository and a tag, while a package is addressed by group, artifact and version,
 * and one repository can hold several artifacts under a group its own name never appears in.
 * Reusing the triple would have to guess that mapping.
 */
@SuppressWarnings("unused")
public class PackagesExtension {

    private final List<PackageSourceEntry> sources = new ArrayList<PackageSourceEntry>();

    /**
     * @return every declared {@code from} entry, in declaration order.
     */
    public List<PackageSourceEntry> getSources() {
        return Collections.unmodifiableList(sources);
    }

    /**
     * Resolves dependencies from a repository's GitHub Packages registry.
     *
     * @param repository the repository, as {@code "owner/repo"}.
     * @return the entry, so a caller holding it can narrow it further.
     */
    public PackageSourceEntry from(String repository) {
        PackageSourceEntry entry = parse(repository);
        sources.add(entry);
        return entry;
    }

    /**
     * Resolves dependencies from a repository's GitHub Packages registry, configured by the given
     * Gradle action.
     *
     * @param repository the repository, as {@code "owner/repo"}.
     * @param action action that configures the entry.
     */
    public void from(String repository, Action<? super PackageSourceEntry> action) {
        PackageSourceEntry entry = parse(repository);
        action.execute(entry);
        sources.add(entry);
    }

    /**
     * Resolves dependencies from a repository's GitHub Packages registry, configured by the given
     * Groovy closure.
     *
     * @param repository the repository, as {@code "owner/repo"}.
     * @param closure closure that configures the entry.
     */
    public void from(String repository, Closure<?> closure) {
        PackageSourceEntry entry = parse(repository);
        if (closure != null) {
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.setDelegate(entry);
            closure.call(entry);
        }
        sources.add(entry);
    }

    /**
     * @param repository the declared {@code "owner/repo"} value.
     * @return the parsed entry.
     * @throws IllegalArgumentException if the value is not {@code owner/repo} with both halves
     * present, since the alternative is a URL that answers 404 at resolution time and reads as a
     * missing dependency rather than as a typo.
     */
    private PackageSourceEntry parse(String repository) {
        String value = repository == null ? "" : repository.trim();
        int separator = value.indexOf('/');
        if (separator < 1 || separator == value.length() - 1 || value.indexOf('/', separator + 1) >= 0) {
            throw new IllegalArgumentException("github.packages.from expects \"owner/repo\", got \""
                    + repository + "\".");
        }
        return new PackageSourceEntry(value.substring(0, separator), value.substring(separator + 1));
    }
}
