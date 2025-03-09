package io.github.intisy.gradle.github;

@SuppressWarnings("unused")
public class ResourcesExtension {
    private String repo;

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getRepo() {
        return repo;
    }
}