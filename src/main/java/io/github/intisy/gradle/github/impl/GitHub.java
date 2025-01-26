package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.utils.GradleUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHubBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Handles all the GitHub API related stuff.
 */
public class GitHub {
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
        direction.mkdirs();
        File jar = new File(direction, repoName + "-" + version + ".jar");
        logger.debug("Implementing jar: " + jar.getName());
        if (!jar.exists()) {
            try {
                List<GHRelease> releases = github.getRepository(repoOwner + "/" + repoName).listReleases().toList();
                GHRelease targetRelease = null;
                for (GHRelease release : releases) {
                    if (release.getTagName().equals(version))
                        targetRelease = release;
                }
                if (targetRelease != null) {
                    List<GHAsset> assets = targetRelease.getAssets();
                    if (!assets.isEmpty()) {
                        for (GHAsset asset : assets) {
                            if (asset.getName().equals(repoName + ".jar")) {
                                download(logger, jar, asset, repoName, repoOwner);
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
        } else
            return jar;
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
    public static void download(Logger logger, File direction, GHAsset asset, String repoName, String repoOwner) throws IOException {
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
        } else {
            byte[] bytes = response.body().bytes();
            try (FileOutputStream fos = new FileOutputStream(direction)) {
                fos.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
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
            System.err.println("Error fetching releases: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String getLatestVersion(Logger logger, String repoOwner, String repoName, org.kohsuke.github.GitHub github) {
        GHRelease latestRelease = getLatestRelease(logger, repoOwner, repoName, github);
        return latestRelease.getTagName();
    }
}
