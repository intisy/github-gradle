package io.github.intisy.gradle.github.plugin.extension;

/**
 * One GitHub Packages repository a build resolves dependencies from, declared by
 * {@link PackagesExtension#from}.
 */
@SuppressWarnings("unused")
public class PackageSourceEntry {

    private final String owner;
    private final String repo;
    private String group;

    /**
     * @param owner the account or organization that owns the repository.
     * @param repo the repository name, without the owner prefix.
     */
    public PackageSourceEntry(String owner, String repo) {
        this.owner = owner;
        this.repo = repo;
    }

    /**
     * @return the account or organization that owns the repository.
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @return the repository name, without the owner prefix.
     */
    public String getRepo() {
        return repo;
    }

    /**
     * Narrows this repository to one dependency group, so resolving anything else never queries it.
     *
     * @param group the Maven group this repository serves, e.g. {@code "com.example"}; null leaves
     * it open to every group.
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * @return the group this repository is narrowed to, or null when it is open to every group.
     */
    public String getGroup() {
        return group;
    }

    /**
     * @return the Maven repository URL for this entry.
     */
    public String getUrl() {
        return "https://maven.pkg.github.com/" + owner + "/" + repo;
    }

    /**
     * @return {@code owner/repo}, as it was declared.
     */
    @Override
    public String toString() {
        return owner + "/" + repo;
    }
}
