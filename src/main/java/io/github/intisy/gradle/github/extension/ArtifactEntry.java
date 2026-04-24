package io.github.intisy.gradle.github.extension;

import java.io.File;

/**
 * A single JAR artifact to include in a GitHub release.
 *
 * <p>Configure multiple entries inside a {@code publishGithub { artifacts { } }} block:
 *
 * <pre>
 * publishGithub {
 *     artifacts {
 *         artifact {
 *             jar        = file("build/libs/my-lib.jar")
 *             classifier = ""        // default artifact — uploaded as repo.jar
 *         }
 *         artifact {
 *             jar        = file("build/libs/my-lib-api.jar")
 *             classifier = "api"     // uploaded as repo-api.jar
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>A blank or null classifier produces the asset name {@code repo.jar}.
 * Any other classifier produces {@code repo-CLASSIFIER.jar}.
 */
@SuppressWarnings("unused")
public class ArtifactEntry {

    private File jar;
    private String classifier = "";

    /**
     * Sets the JAR file to upload for this artifact.
     *
     * @param jar the JAR file
     */
    public void setJar(File jar) {
        this.jar = jar;
    }

    /**
     * @return the JAR file to upload, or null if not set
     */
    public File getJar() {
        return jar;
    }

    /**
     * Sets the classifier that distinguishes this artifact from others in the same release.
     * A blank or null classifier makes this the default artifact ({@code repo.jar}).
     *
     * @param classifier the classifier string, e.g. {@code "api"}, {@code "fat"}
     */
    public void setClassifier(String classifier) {
        this.classifier = classifier == null ? "" : classifier;
    }

    /**
     * @return the classifier, or an empty string for the default artifact
     */
    public String getClassifier() {
        return classifier == null ? "" : classifier;
    }
}
