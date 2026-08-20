package io.github.intisy.gradle.github.plugin.extension;

import org.gradle.api.Action;
import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extension for declaring dependencies resolved from an arbitrary git host or a direct jar URL,
 * as opposed to a GitHub release (see {@code github { }}).
 *
 * <pre>
 * sources {
 *     git {
 *         url = "https://gitlab.com/me/lib.git"
 *         ref = "main"
 *         into = "implementation"
 *     }
 *     jar {
 *         url = "https://nexus.internal/libs/foo-1.0.jar"
 *         header "Authorization", "Bearer ${myToken}"
 *         sha256 = "..."
 *         into = "implementation"
 *     }
 * }
 * </pre>
 *
 * <p>Both {@code git { }} and {@code jar { }} are repeatable.
 */
@SuppressWarnings("unused")
public class SourcesExtension {

    private final List<GitSourceEntry> gitSources = new ArrayList<GitSourceEntry>();
    private final List<JarSourceEntry> jarSources = new ArrayList<JarSourceEntry>();

    /**
     * @return every declared {@code git { } } entry, in declaration order.
     */
    public List<GitSourceEntry> getGitSources() {
        return Collections.unmodifiableList(gitSources);
    }

    /**
     * @return every declared {@code jar { } } entry, in declaration order.
     */
    public List<JarSourceEntry> getJarSources() {
        return Collections.unmodifiableList(jarSources);
    }

    /**
     * Declares a git repository dependency, configured by the given Gradle action.
     *
     * @param action action that configures a {@link GitSourceEntry}.
     */
    public void git(Action<? super GitSourceEntry> action) {
        GitSourceEntry entry = new GitSourceEntry();
        action.execute(entry);
        gitSources.add(entry);
    }

    /**
     * Declares a git repository dependency, configured by the given Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code git { ... }}
     *
     * @param closure closure that configures a {@link GitSourceEntry}.
     */
    public void git(Closure<?> closure) {
        GitSourceEntry entry = new GitSourceEntry();
        if (closure != null) {
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.setDelegate(entry);
            closure.call(entry);
        }
        gitSources.add(entry);
    }

    /**
     * Declares a direct jar URL dependency, configured by the given Gradle action.
     *
     * @param action action that configures a {@link JarSourceEntry}.
     */
    public void jar(Action<? super JarSourceEntry> action) {
        JarSourceEntry entry = new JarSourceEntry();
        action.execute(entry);
        jarSources.add(entry);
    }

    /**
     * Declares a direct jar URL dependency, configured by the given Groovy closure.
     * Supports Gradle Groovy DSL usage: {@code jar { ... }}
     *
     * @param closure closure that configures a {@link JarSourceEntry}.
     */
    public void jar(Closure<?> closure) {
        JarSourceEntry entry = new JarSourceEntry();
        if (closure != null) {
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.setDelegate(entry);
            closure.call(entry);
        }
        jarSources.add(entry);
    }
}
