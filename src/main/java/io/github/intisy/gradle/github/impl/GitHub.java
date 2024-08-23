package io.github.intisy.gradle.github.impl;

import io.github.intisy.gradle.github.utils.GradleUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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
    /**
     * gets the asset from the repository
     */
    public static File getAsset(String repoName, String repoOwner, String version) {
        File direction = new File(new File(GradleUtils.getGradleHome().toFile(), repoOwner), repoName);
        direction.mkdirs();
        File jar = new File(direction, version + ".jar");
        if (!jar.exists()) {
            try {
                org.kohsuke.github.GitHub github = org.kohsuke.github.GitHub.connectAnonymously();
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
                                download(jar, asset, repoName, repoOwner);
                                return jar;
                            }
                        }
                    } else {
                        throw new RuntimeException("No assets found for the release");
                    }
                } else {
                    throw new RuntimeException("Release not found");
                }
            } catch (IOException e) {
                throw new RuntimeException("Github exception while pulling asset: " + e.getMessage() + " (retrying in 5 seconds...)");
            }
            throw new RuntimeException("Could not find an valid asset");
        } else
            return jar;
    }
    /**
     * downloads the asset
     */
    public static void download(File direction, GHAsset asset, String repoName, String repoOwner) throws IOException {
        System.out.println("Downloading dependency from " + repoOwner + "/" + repoName);
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
        System.out.println("Download completed for dependency " + repoOwner + "/" + repoName);
    }
}
