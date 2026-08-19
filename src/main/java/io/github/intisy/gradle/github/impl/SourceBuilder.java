package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.api.config.GitHubConfig;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
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
 *
 * @implNote {@link GitHub} carries a single {@link ResourcesExtension}, hence a single repository
 * identity, so a fresh {@link GitHub} is built per {@link #buildFromSource} call, configured for
 * the exact {@code owner}/{@code repo} requested, rather than accepting one pre-built {@link
 * GitHub} that could only ever be correctly configured for one repository.
 */
public class SourceBuilder implements SourceBuilds {
    private final GitHubConfig config;
    private final GitHubLogger logger;
    private final File cacheDir;
    private final BuildInvoker invoker;

    /**
     * @param config the access token and auth/cli/resilience settings used to clone/pull each checkout.
     * @param logger receives diagnostic output.
     * @param cacheDir the directory built jars are cached under, keyed by owner, repo, and resolved commit.
     * @param invoker runs the actual build against a checkout; injected so it can be stubbed in tests.
     */
    public SourceBuilder(GitHubConfig config, GitHubLogger logger, File cacheDir, BuildInvoker invoker) {
        this.config = config;
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
        GitHub gitHub = newGitHub(owner, repo);
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

    private GitHub newGitHub(String owner, String repo) {
        ResourcesExtension resources = new ResourcesExtension();
        resources.setRepoUrl("https://github.com/" + owner + "/" + repo);
        return new GitHub(logger, resources, config);
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
