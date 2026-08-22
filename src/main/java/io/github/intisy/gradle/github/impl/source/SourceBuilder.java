package io.github.intisy.gradle.github.impl.source;

import io.github.intisy.gradle.github.api.config.GitHubConfig;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.api.config.ResourceSettings;
import io.github.intisy.gradle.github.impl.github.GitHub;
import io.github.intisy.gradle.github.utils.CloneUrlIdentity;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Clones an arbitrary git URL and builds it, caching the resulting jar by commit, so a repeated
 * request for the same commit never re-runs the build. Host-agnostic: the caller supplies the
 * clone URL explicitly, so this class never assumes github.com or any other specific host.
 *
 * @implNote {@link GitHub} carries a single {@link ResourceSettings}, hence a single repository
 * identity, so a fresh {@link GitHub} is built per {@link #buildFromSource(String, String, String,
 * String, String) buildFromSource} call (which {@link #buildFromGit} also goes through), scoped to
 * the exact {@code cloneUrl} requested, rather than accepting one pre-built {@link GitHub} that
 * could only ever be correctly configured for one repository.
 */
public class SourceBuilder {
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
     * @param cloneUrl the exact URL to clone from; any git host, not just github.com.
     * @param ref the branch, tag, or commit to build, or null for the remote's default branch.
     * @return the cached jar for the resolved ref, built only when not already cached.
     * @throws IOException if the clone, checkout, or build itself fails.
     */
    public File buildFromGit(String cloneUrl, String ref) throws IOException {
        String[] identity = CloneUrlIdentity.derive(cloneUrl);
        return buildFromSource(cloneUrl, identity[0], identity[1], null, ref);
    }

    /**
     * @param cloneUrl  the exact URL to clone from; any git host, not just github.com.
     * @param ref       the branch, tag, or commit to build, or null for the remote's default branch.
     * @param gradleDir the gradle project directory relative to the checkout root, or null when the
     *                  build lives at the root.
     * @param modules   the module names whose jars to take, or empty for the root project's own jar.
     * @return the cached jars for the resolved commit, in {@code modules} order.
     * @throws IOException if the clone, checkout, or build itself fails.
     */
    public List<File> buildFromGit(String cloneUrl, String ref, String gradleDir, List<String> modules) throws IOException {
        String[] identity = CloneUrlIdentity.derive(cloneUrl);
        return buildModules(cloneUrl, identity[0], identity[1], null, ref, gradleDir, modules);
    }

    /**
     * @param cloneUrl  the exact URL to clone from; any git host, not just github.com.
     * @param owner     an identity for the repository, used only for the checkout/cache directory
     *                  and jar naming, never for URL construction.
     * @param repo      an identity for the repository, used only for the checkout/cache directory
     *                  and jar naming, never for URL construction.
     * @param branch    the branch to clone or pull, or null for the current/default branch.
     * @param commitSha the commit to check out, or null to use the branch's latest commit.
     * @return the cached jar for the resolved commit, built only when not already cached.
     * @throws IOException if the clone/pull, checkout, or build itself fails.
     */
    public File buildFromSource(String cloneUrl, String owner, String repo, String branch, String commitSha) throws IOException {
        return buildModules(cloneUrl, owner, repo, branch, commitSha, null, Collections.<String>emptyList()).get(0);
    }

    /**
     * @implNote One clone, one build, then one cached jar per module, because a multi-module build
     * produces every module's jar in a single {@code gradlew build} and re-running it per module
     * would pay the whole build again for each. The cache key carries the module name so two modules
     * of one commit never collide.
     */
    private List<File> buildModules(String cloneUrl, String owner, String repo, String branch, String commitSha,
            String gradleDir, List<String> modules) throws IOException {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + cacheDir.getAbsolutePath());
        }
        File checkoutDir = new File(cacheDir, owner + "-" + repo);
        GitHub gitHub = newGitHub(cloneUrl);
        gitHub.cloneOrPullFromUrl(checkoutDir, cloneUrl, owner, branch);
        String sha = checkOutAndResolve(checkoutDir, commitSha);

        List<String> wanted = modules.isEmpty() ? Collections.singletonList("") : modules;
        List<File> cached = new ArrayList<File>();
        List<String> missing = new ArrayList<String>();
        for (String module : wanted) {
            File cachedJar = cacheFileFor(owner, repo, sha, module);
            if (cachedJar.isFile()) {
                logger.debug("Using cached build for " + owner + "/" + repo + "@" + sha
                        + (module.isEmpty() ? "" : ":" + module) + ": " + cachedJar.getName());
            } else {
                missing.add(module);
            }
            cached.add(cachedJar);
        }
        if (missing.isEmpty()) {
            return cached;
        }

        logger.log("Building " + owner + "/" + repo + "@" + sha + " from source.");
        File buildRoot = gradleDir == null || gradleDir.isEmpty() ? checkoutDir : new File(checkoutDir, gradleDir);
        if (!buildRoot.isDirectory()) {
            throw new IOException("Gradle directory " + buildRoot.getAbsolutePath() + " does not exist in the checkout.");
        }
        invoker.invoke(buildRoot);
        for (String module : missing) {
            File builtJar = module.isEmpty()
                    ? locateBuiltJar(buildRoot, repo)
                    : locateModuleJar(buildRoot, module);
            Files.copy(builtJar.toPath(), cacheFileFor(owner, repo, sha, module).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        return cached;
    }

    private File cacheFileFor(String owner, String repo, String sha, String module) {
        String suffix = module.isEmpty() ? "" : "-" + module;
        return new File(cacheDir, owner + "-" + repo + "-" + sha + suffix + ".jar");
    }

    private GitHub newGitHub(String cloneUrl) {
        ResourceSettings resources = new ResourceSettings();
        resources.setRepoUrl(cloneUrl);
        return new GitHub(logger, resources, config);
    }

    private String checkOutAndResolve(File checkoutDir, String ref) throws IOException {
        try (Git git = Git.open(checkoutDir)) {
            if (ref != null) {
                checkOutRef(git, ref);
            }
            ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) {
                throw new IOException("Could not resolve HEAD in " + checkoutDir.getAbsolutePath() + ".");
            }
            return head.getName();
        } catch (GitAPIException e) {
            throw new IOException("Failed to check out " + (ref != null ? ref : "HEAD")
                    + " in " + checkoutDir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    /**
     * @implNote Tries a local branch, then a remote-tracking branch, before falling back to
     * generic ref resolution (a tag or a commit sha), matching porcelain {@code git checkout
     * <name>}'s own precedence: a branch wins over a tag of the same name. Both {@link
     * Repository#resolve} and {@link org.eclipse.jgit.api.CheckoutCommand}'s own short-name
     * resolution follow the same {@code rev-parse}-style order, checking {@code refs/tags/<name>}
     * before {@code refs/heads/<name>}, so every branch checkout here passes the fully-qualified
     * {@code refs/heads/<name>}, never the bare short name, to {@code setName}: passing the short
     * name hands the ambiguity straight back to {@code CheckoutCommand} and lands on the tag
     * instead. This is true even for {@code CheckoutCommand.setCreateBranch(true)}: measured
     * directly, it creates {@code refs/heads/<ref>} at the correct commit as a side effect, but
     * then still re-resolves the bare {@code ref} for the actual HEAD-attach and working-tree
     * update, landing on the tag's commit with a detached HEAD. So the remote-tracking branch is
     * created with the plain {@link org.eclipse.jgit.api.CreateBranchCommand} (which only ever
     * touches {@code refs/heads/}, no ambiguity possible) and checked out as a separate, explicit,
     * fully-qualified step. A fresh clone creates a local branch only for the one it checks out by
     * default, so any other branch exists solely as {@code refs/remotes/origin/<ref>} until this
     * runs; the direct {@code refs/heads/<ref>} check above still covers a second call against an
     * already-checked-out branch.
     */
    private void checkOutRef(Git git, String ref) throws GitAPIException, IOException {
        Repository repository = git.getRepository();
        Ref localBranch = repository.findRef("refs/heads/" + ref);
        if (localBranch != null) {
            git.checkout().setName(localBranch.getName()).call();
            return;
        }
        Ref remoteBranch = repository.findRef("refs/remotes/origin/" + ref);
        if (remoteBranch != null) {
            git.branchCreate()
                    .setName(ref)
                    .setStartPoint(remoteBranch.getName())
                    .setUpstreamMode(SetupUpstreamMode.TRACK)
                    .call();
            git.checkout().setName("refs/heads/" + ref).call();
            return;
        }
        if (repository.resolve(ref) != null) {
            git.checkout().setName(ref).call();
            return;
        }
        throw new IOException("Ref " + ref + " cannot be resolved as a local branch, a remote branch "
                + "on origin, a tag, or a commit.");
    }

    /**
     * @implNote Looks under the module's own {@code build/libs} rather than the root's, because a
     * multi-module gradle build writes each module's jar there and writes nothing to the root.
     */
    private File locateModuleJar(File buildRoot, String module) throws IOException {
        File libsDir = new File(new File(new File(buildRoot, module), "build"), "libs");
        File[] files = libsDir.listFiles();
        List<File> candidates = new ArrayList<File>();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".jar")) continue;
                if (name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar")) continue;
                candidates.add(file);
            }
        }
        if (candidates.isEmpty()) {
            throw new IOException("No built jar found for module " + module + " under " + libsDir.getAbsolutePath() + ".");
        }
        if (candidates.size() > 1) {
            throw new IOException("Multiple candidate jars found for module " + module + " under "
                    + libsDir.getAbsolutePath() + ": " + candidates);
        }
        return candidates.get(0);
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
