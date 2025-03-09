package io.github.intisy.gradle.github;

@SuppressWarnings("unused")
public class ResourcesExtension {
    private String repo;
    private boolean buildOnly;

    public boolean isBuildOnly() {
        return buildOnly;
    }

    public void setBuildOnly(boolean buildOnly) {
        this.buildOnly = buildOnly;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getRepo() {
        return repo;
    }
}