package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.GitHubLogger;
import io.github.intisy.gradle.github.api.SourceBuilds;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a GitHub repository from source and caches the resulting jar by commit, so a repeated
 * request for the same commit never re-runs the build.
 */
public class SourceBuilder implements SourceBuilds {
    private final GitHub gitHub;
    private final GitHubLogger logger;
    private final File cacheDir;
    private final BuildInvoker invoker;

    public SourceBuilder(GitHub gitHub, GitHubLogger logger, File cacheDir, BuildInvoker invoker) {
        this.gitHub = gitHub;
        this.logger = logger;
        this.cacheDir = cacheDir;
        this.invoker = invoker;
    }

    /**
     * @param owner     the repository owner.
     * @param repo      the repository name.
     * @param branch    the branch to clone or pull, or null for the current/default branch.
     * @param commitSha the commit to check out, or null to use the branch's latest commit.
     * @return the cached jar for the resolved commit, built only when not already cached.
     */
    @Override
    public File buildFromSource(String owner, String repo, String branch, String commitSha) throws IOException {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + cacheDir.getAbsolutePath());
        }
        File checkoutDir = new File(cacheDir, owner + "-" + repo);
        gitHub.cloneOrPull(checkoutDir, owner, repo, branch);
        String sha = checkOutAndResolve(checkoutDir, commitSha);

        File cachedJar = new File(cacheDir, owner + "-" + repo + "-" + sha + ".jar");
        if (cachedJar.isFile()) {
            logger.debug("Using cached build for " + owner + "/" + repo + "@" + sha + ": " + cachedJar.getName());
            return cachedJar;
        }

        logger.log("Building " + owner + "/" + repo + "@" + sha + " from source.");
        invoker.invoke(checkoutDir);
        File builtJar = locateBuiltJar(checkoutDir, repo);
        Files.copy(builtJar.toPath(), cachedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return cachedJar;
    }

    private String checkOutAndResolve(File checkoutDir, String commitSha) throws IOException {
        try (Git git = Git.open(checkoutDir)) {
            if (commitSha != null) {
                git.checkout().setName(commitSha).call();
            }
            ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) {
                throw new IOException("Could not resolve HEAD in " + checkoutDir.getAbsolutePath() + ".");
            }
            return head.getName();
        } catch (GitAPIException e) {
            throw new IOException("Failed to check out " + (commitSha != null ? commitSha : "HEAD")
                    + " in " + checkoutDir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    private File locateBuiltJar(File checkoutDir, String repo) throws IOException {
        File libsDir = new File(new File(checkoutDir, "build"), "libs");
        File[] files = libsDir.listFiles();
        List<File> candidates = new ArrayList<File>();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".jar")) continue;
                if (name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar")) continue;
                if (!name.contains(repo)) continue;
                candidates.add(file);
            }
        }
        if (candidates.isEmpty()) {
            throw new IOException("No built jar found under " + libsDir.getAbsolutePath() + ".");
        }
        if (candidates.size() > 1) {
            throw new IOException("Multiple candidate jars found under " + libsDir.getAbsolutePath() + ": " + candidates);
        }
        return candidates.get(0);
    }
}
