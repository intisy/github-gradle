package io.github.intisy.gradle.github.extension;

import java.io.File;

/**
 * Extension for configuring the {@code publishGithub} task.
 * Register it in your build.gradle inside a {@code publish { }} block:
 *
 * <pre>
 * github {
 *     publish {
 *         owner   = "my-org"
 *         repo    = "my-repo"
 *         version = "2.0.0"
 *         jar     = file("build/libs/my-fat.jar")
 *     }
 * }
 * </pre>
 *
 * Every field is optional and falls back to auto-detection when null:
 * <ul>
 *   <li>{@code owner} / {@code repo} — parsed from the git remote {@code origin} URL</li>
 *   <li>{@code version} — taken from {@code project.version}</li>
 *   <li>{@code jar} — the shadow/fat JAR in {@code build/libs/}, or the first regular JAR</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class PublishExtension {

    private String owner;
    private String repo;
    private String version;
    private File jar;

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
     * Override the JAR file to upload.
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
}
