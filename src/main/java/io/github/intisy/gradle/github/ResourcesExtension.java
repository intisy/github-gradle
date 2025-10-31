package io.github.intisy.gradle.github;

/**
 * Extension for configuring external resources to be used in the project.
 */
@SuppressWarnings("unused")
public class ResourcesExtension {
    private String branch = "main";
    private String path = "/";
    private String repoUrl;
    private boolean buildOnly;

    /**
     * @return Whether to only build the resources without including them in the project.
     */
    public boolean isBuildOnly() {
        return buildOnly;
    }

    /**
     * @param buildOnly Whether to only build the resources without including them in the project.
     */
    public void setBuildOnly(boolean buildOnly) {
        this.buildOnly = buildOnly;
    }

    /**
     * @param repoUrl The URL of the repository.
     * @deprecated Use {@link #setRepoUrl(String)} instead.
     */
    @Deprecated
    public void setRepo(String repoUrl) {
        setRepoUrl(repoUrl);
    }

    /**
     * @param repoUrl The URL of the repository.
     */
    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    /**
     * @return The URL of the repository.
     */
    public String getRepoUrl() {
        return repoUrl;
    }

    /**
     * @return The branch of the repository to use.
     */
    public String getBranch() {
        return branch;
    }

    /**
     * @param branch The branch of the repository to use.
     */
    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * @return The path within the repository to the resources.
     */
    public String getPath() {
        return path;
    }

    /**
     * @param path The path within the repository to the resources.
     */
    public void setPath(String path) {
        this.path = path;
    }
}