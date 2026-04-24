package io.github.intisy.gradle.github.extension;

import org.gradle.api.Action;
import groovy.lang.Closure;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension for configuring the {@code publishGithub} task.
 *
 * <p>All fields are optional and fall back to auto-detection when null:
 * <ul>
 *   <li>{@code owner} / {@code repo} — parsed from the git remote {@code origin} URL</li>
 *   <li>{@code version} — taken from {@code project.version}</li>
 *   <li>{@code jar} — the shadow/fat JAR in {@code build/libs/}, or the first regular JAR</li>
 * </ul>
 *
 * <p>Single-JAR shorthand (backward-compatible):
 * <pre>
 * publishGithub {
 *     owner   = "my-org"
 *     repo    = "my-repo"
 *     version = "2.0.0"
 *     jar     = file("build/libs/my-lib.jar")
 * }
 * </pre>
 *
 * <p>Multi-JAR with classifiers:
 * <pre>
 * publishGithub {
 *     artifacts {
 *         artifact { classifier = "";    jar = file("build/libs/my-lib.jar") }
 *         artifact { classifier = "api"; jar = file("build/libs/my-lib-api.jar") }
 *         artifact { classifier = "fat"; jar = file("build/libs/my-lib-fat.jar") }
 *     }
 * }
 * </pre>
 */
@SuppressWarnings("unused")
public class PublishExtension {

    private String owner;
    private String repo;
    private String version;
    private String tag;
    private String releaseName;
    private File jar;
    private final List<ArtifactEntry> artifacts = new ArrayList<ArtifactEntry>();

    /**
     * Override the GitHub repository owner.
     * When null (default) the owner is auto-detected from the git remote origin.
     *
     * @param owner the repository owner
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * @return the overridden owner, or null to auto-detect
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Override the GitHub repository name.
     * When null (default) the repo name is auto-detected from the git remote origin.
     *
     * @param repo the repository name
     */
    public void setRepo(String repo) {
        this.repo = repo;
    }

    /**
     * @return the overridden repo name, or null to auto-detect
     */
    public String getRepo() {
        return repo;
    }

    /**
     * Override the release version tag.
     * When null (default) the version is taken from {@code project.version}.
     *
     * @param version the version tag to use for the GitHub release
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return the overridden version, or null to use {@code project.version}
     */
    public String getVersion() {
        return version;
    }

    /**
     * Override the git tag pushed to GitHub for this release.
     * When null (default) the tag equals {@link #getVersion()} (after fallback to {@code project.version}).
     * Use this to prefix or format the tag independently of the version string,
     * e.g. {@code tag = "v1.0"} while {@code version = "1.0"}.
     *
     * @param tag the tag name, e.g. {@code "v2.0.0"}
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * @return the overridden tag, or null to use the resolved version string
     */
    public String getTag() {
        return tag;
    }

    /**
     * Override the human-readable release title shown on GitHub.
     * When null (default) the title equals the resolved tag.
     * Use this to set a descriptive name, e.g. {@code releaseName = "Release 1.0"}.
     *
     * @param releaseName the release title
     */
    public void setReleaseName(String releaseName) {
        this.releaseName = releaseName;
    }

    /**
     * @return the overridden release title, or null to use the resolved tag
     */
    public String getReleaseName() {
        return releaseName;
    }

    /**
     * Override the single JAR file to upload.
     * Ignored when {@link #getArtifacts()} is non-empty.
     * When null (default) the task auto-selects the shadow/fat JAR from
     * {@code build/libs/}, or falls back to the first regular JAR found there.
     *
     * @param jar the JAR file to upload
     */
    public void setJar(File jar) {
        this.jar = jar;
    }

    /**
     * @return the overridden JAR file, or null to auto-select
     */
    public File getJar() {
        return jar;
    }

    /**
     * Returns the list of explicit artifacts to upload.
     * When non-empty this list takes precedence over the single {@link #getJar()} field.
     *
     * @return mutable list of {@link ArtifactEntry} instances
     */
    public List<ArtifactEntry> getArtifacts() {
        return artifacts;
    }

    /**
     * Adds a single artifact entry, configured by the given Gradle action.
     *
     * @param action action that receives and configures an {@link ArtifactEntry}
     */
    public void artifact(Action<? super ArtifactEntry> action) {
        ArtifactEntry entry = new ArtifactEntry();
        action.execute(entry);
        artifacts.add(entry);
    }

    /**
     * Adds a single artifact entry, configured by the given Groovy closure.
     *
     * @param closure closure that configures an {@link ArtifactEntry}
     */
    public void artifact(Closure<?> closure) {
        ArtifactEntry entry = new ArtifactEntry();
        if (closure != null) {
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.setDelegate(entry);
            closure.call(entry);
        }
        artifacts.add(entry);
    }

    /**
     * Opens a configuration block in which multiple {@link #artifact} calls can be made.
     * This is a convenience wrapper so users can write {@code artifacts { artifact { } }}.
     *
     * @param action action that calls {@link #artifact} one or more times on this extension
     */
    public void artifacts(Action<? super PublishExtension> action) {
        action.execute(this);
    }

    /**
     * Opens a Groovy-DSL configuration block for multiple artifact entries.
     *
     * @param closure closure in which {@code artifact { }} calls are made
     */
    public void artifacts(Closure<?> closure) {
        if (closure == null) return;
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(this);
        closure.call(this);
    }
}
