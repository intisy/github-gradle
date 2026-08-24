package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.capability.Downloads;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.plugin.extension.GitSourceEntry;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.plugin.extension.JarSourceEntry;
import io.github.intisy.gradle.github.plugin.extension.SourcesExtension;
import io.github.intisy.gradle.github.utils.UrlRedaction;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;

/**
 * Resolves every {@code sources { git { } / jar { } } } entry into a local jar and adds it to its
 * configured native Gradle configuration, after the project is evaluated.
 */
public class SourcesResolution {

    /**
     * @param project the project whose {@code sources} extension is resolved.
     * @param logger receives diagnostic output.
     * @param sourcesExtension the extension supplying the declared {@code git}/{@code jar} entries.
     * @param sourceBuilds resolves each {@code git} entry to a jar.
     * @param downloads resolves each {@code jar} entry to a jar.
     * @param addedJars the dedup filter shared with
     * {@link DependencyResolution#apply(Project, Logger, GithubExtension, Releases, AddedJars)}, so a jar
     * reachable from both a {@code github*} coordinate and a {@code sources} entry is added to a
     * native configuration only once.
     */
    public static void apply(Project project, Logger logger, SourcesExtension sourcesExtension,
            SourceBuilds sourceBuilds, Downloads downloads, AddedJars addedJars) {
        project.afterEvaluate(proj -> {
            for (GitSourceEntry entry : sourcesExtension.getGitSources()) {
                if (entry.getUrl() == null) {
                    throw new IllegalStateException("A sources { git { } } entry is missing 'url'.");
                }
                try {
                    for (File jar : sourceBuilds.buildFromGit(entry.getUrl(), entry.getRef(),
                            entry.getDir(), entry.getModules())) {
                        addJar(proj, entry.getInto(), jar, addedJars);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to build git source " + UrlRedaction.redact(entry.getUrl())
                            + ": " + e.getMessage(), e);
                }
            }
            for (JarSourceEntry entry : sourcesExtension.getJarSources()) {
                if (entry.getUrl() == null) {
                    throw new IllegalStateException("A sources { jar { } } entry is missing 'url'.");
                }
                try {
                    File jar = downloads.download(entry.getUrl(), entry.getHeaders(), entry.getSha256());
                    addJar(proj, entry.getInto(), jar, addedJars);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to download jar source " + UrlRedaction.redact(entry.getUrl())
                            + ": " + e.getMessage(), e);
                }
            }
        });
    }

    private static void addJar(Project proj, String nativeCfg, File jar, AddedJars addedJars) {
        if (addedJars.add(nativeCfg, jar)) {
            proj.getDependencies().add(nativeCfg, proj.files(jar));
        }
    }
}
