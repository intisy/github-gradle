package io.github.intisy.gradle.github;

@SuppressWarnings("unused")
public class ResourcesExtension {
    private String branch = "main";
    private String path = "/";
    private String repoUrl;
    private boolean buildOnly;

    public boolean isBuildOnly() {
        return buildOnly;
    }

    public void setBuildOnly(boolean buildOnly) {
        this.buildOnly = buildOnly;
    }

    @Deprecated
    public void setRepo(String repoUrl) {
        setRepoUrl(repoUrl);
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}