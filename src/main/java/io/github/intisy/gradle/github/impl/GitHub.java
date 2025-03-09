package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.utils.GradleUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.gradle.internal.impldep.org.eclipse.jgit.api.Git;
import org.gradle.internal.impldep.org.eclipse.jgit.api.PullCommand;
import org.gradle.internal.impldep.org.eclipse.jgit.api.PullResult;
import org.gradle.internal.impldep.org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.internal.impldep.org.eclipse.jgit.lib.ObjectId;
import org.gradle.internal.impldep.org.eclipse.jgit.lib.Ref;
import org.gradle.internal.impldep.org.eclipse.jgit.lib.Repository;
import org.gradle.internal.impldep.org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.gradle.internal.impldep.org.eclipse.jgit.transport.CredentialsProvider;
import org.gradle.internal.impldep.org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Handles all the GitHub API related stuff.
 */
public class GitHub {
    public static void cloneRepository(Logger logger, File path, String repoOwner, String repoName, String apiKey) throws GitAPIException {
        String repositoryURL = "https://github.com/" + repoOwner + "/" + repoName;
        logger.log("Cloning repository... (" + repositoryURL + ")");
        CredentialsProvider credentialsProvider = apiKey != null ? new UsernamePasswordCredentialsProvider(repoOwner, apiKey) : null;
        try (Git git = Git.cloneRepository()
                .setURI(repositoryURL)
                .setCredentialsProvider(credentialsProvider)
                .setDirectory(path)
                .call()) {
            logger.log("Repository cloned successfully.");
        }
    }

    public static boolean doesRepoExist(File path) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(path.toPath().resolve(".git").toFile())
                .readEnvironment()
                .findGitDir()
                .build()) {
            return repository.getObjectDatabase().exists();
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isRepoUpToDate(Logger logger, File path) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (
             Repository repository = builder.setGitDir(path.toPath().resolve(".git").toFile())
                .readEnvironment()
                .findGitDir()
                .build();
             Git git = new Git(repository)
        ) {
            git.fetch().call();
            String branch = repository.getBranch();
            ObjectId localCommit = repository.resolve("refs/heads/" + branch);
            ObjectId remoteCommit = repository.resolve("refs/remotes/origin/" + branch);
            if (localCommit != null && remoteCommit != null) {
                return localCommit.equals(remoteCommit);
            } else {
                return false;
            }
        } catch (IOException | GitAPIException exception) {
            return false;
        }
    }

    public static void pullRepository(Logger logger, File path, String repoOwner, String apiKey) throws GitAPIException, IOException {
        CredentialsProvider credentialsProvider = apiKey != null ? new UsernamePasswordCredentialsProvider(repoOwner, apiKey) : null;
        try (Git repo = Git.open(path)) {
            Repository repository = repo.getRepository();
            Git git = new Git(repository);
            git.fetch().setCredentialsProvider(credentialsProvider).call();
            List<Ref> branches = git.branchList().call();
            if (branches.size() > 1) {
                logger.warn("Repository has multiple branches, might pull wrong branch...");
                for (Ref branch : branches) {
                    logger.warn("Branch: " + branch.getName());
                }
            }
            PullCommand pullCmd = git.pull()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemoteBranchName(branches.get(0).getName());
            logger.log("Pulling Repository branch" + branches.get(0).getName());
            PullResult result = pullCmd.call();
            if (!result.isSuccessful()) {
                logger.error("Pull failed: " + branches.get(0).getName());
            } else {
                logger.log("Successfully pulled repository.");
            }
        }
    }
    public static void cloneOrPullRepository(Logger logger, File path, String repoOwner, String repoName, String apiKey) throws GitAPIException, IOException {
        if (doesRepoExist(path)) {
            if (!isRepoUpToDate(logger, path))
                pullRepository(logger, path, repoOwner, apiKey);
            else {
                logger.log("Repository is up to date.");
            }
        } else {
            cloneRepository(logger, path, repoOwner, repoName, apiKey);
        }
    }
    /**
     * Retrieves the asset (JAR file) from the specified GitHub repository.
     *
     * @param repoName The name of the GitHub repository.
     * @param repoOwner The owner of the GitHub repository.
     * @param version The version of the asset to retrieve.
     * @throws RuntimeException If the asset cannot be found or downloaded.
     * @return The local file representing the downloaded asset.
     * <p>
     * This function first checks if the asset already exists locally.
     * If not, it connects to the GitHub API,
     * retrieves the specified release, and then downloads the asset.
     * The downloaded asset is saved in a local
     * directory based on the repository owner and name.
     *
     * If the asset cannot be found or downloaded, a RuntimeException is thrown.
     */
    public static File getAsset(Logger logger, String repoName, String repoOwner, String version, org.kohsuke.github.GitHub github) {
        File direction = new File(GradleUtils.getGradleHome().toFile(), repoOwner);
        if (!direction.exists() && !direction.mkdirs())
            throw new RuntimeException("Failed to create directory: " + direction.getAbsolutePath());
        File jar = new File(direction, repoName + "-" + version + ".jar");
        logger.debug("Starting the process to implement jar: " + jar.getName());
        if (!jar.exists()) {
            try {
                List<GHRelease> releases = github.getRepository(repoOwner + "/" + repoName).listReleases().toList();
                GHRelease targetRelease = null;
                for (GHRelease release : releases) {
                    if (release.getTagName().equals(version))
                        targetRelease = release;
                }
                if (targetRelease != null) {
                    List<GHAsset> assets = targetRelease.listAssets().toList();
                    if (!assets.isEmpty()) {
                        for (GHAsset asset : assets) {
                            if (asset.getName().equals(repoName + ".jar")) {
                                downloadAsset(logger, jar, asset, repoName, repoOwner);
                                return jar;
                            }
                        }
                    } else {
                        throw new RuntimeException("No assets found for the release for " + repoOwner + ":" + repoName);
                    }
                } else {
                    throw new RuntimeException("Release not found for " + repoOwner + ":" + repoName);
                }
            } catch (IOException e) {
                throw new RuntimeException("Github exception while pulling asset: " + e.getMessage() + " (retrying in 5 seconds...)");
            }
            throw new RuntimeException("Could not find an valid asset for " + repoOwner + ":" + repoName);
        } else {
            logger.debug("Jar already exists: " + jar.getName());
            return jar;
        }
    }
    /**
     * Downloads the asset.
     *
     * @param direction The local file representing the downloaded asset.
     * @param asset The GitHub asset to download.
     * @param repoName The name of the GitHub repository.
     * @param repoOwner The owner of the GitHub repository.
     * @throws IOException If an error occurs while downloading the asset.
     */
    public static void downloadAsset(Logger logger, File direction, GHAsset asset, String repoName, String repoOwner) throws IOException {
        logger.log("Downloading dependency from " + repoOwner + "/" + repoName);
        String downloadUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/assets/" + asset.getId();
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(downloadUrl)
                .addHeader("Accept", "application/octet-stream")
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            response.close();
            throw new IOException("Failed to download asset: " + response);
        } else if (response.body() != null) {
            byte[] bytes = response.body().bytes();
            try (FileOutputStream fos = new FileOutputStream(direction)) {
                fos.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        logger.log("Download completed for dependency " + repoOwner + "/" + repoName);
    }
    public static GHRelease getLatestRelease(Logger logger, String repoOwner, String repoName, org.kohsuke.github.GitHub github) {
        try {
            logger.debug("Fetching releases from GitHub " + repoOwner + "/" + repoName);
            List<GHRelease> releases = github.getRepository(repoOwner + "/" + repoName).listReleases().toList();
            logger.debug("Found releases for " + repoName + ": " + releases);
            if (!releases.isEmpty()) {
                logger.debug("Latest release found for " + repoName + ": " + releases.get(0).getTagName());
                return releases.get(0);
            } else
                throw new RuntimeException("No releases found for " + repoName);
        } catch (IOException e) {
            logger.error("Error fetching releases: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String getLatestVersion(Logger logger, String repoOwner, String repoName, org.kohsuke.github.GitHub github) {
        GHRelease latestRelease = getLatestRelease(logger, repoOwner, repoName, github);
        return latestRelease.getTagName();
    }
}
