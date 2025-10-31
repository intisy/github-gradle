package io.github.intisy.gradle.github.impl;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.github.intisy.gradle.github.GithubExtension;
import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.ResourcesExtension;
import io.github.intisy.gradle.github.utils.GradleUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig;
import org.eclipse.jgit.util.FS;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

@SuppressWarnings("unused")
public class GitHub {
    private final Logger logger;
    private final ResourcesExtension resourcesExtension;
    private final GithubExtension githubExtension;
    private String resolvedApiKey;

    public GitHub(Logger logger, ResourcesExtension resourcesExtension, GithubExtension githubExtension) {
        this.logger = logger;
        this.resourcesExtension = resourcesExtension;
        this.githubExtension = githubExtension;
        this.resolvedApiKey = null;
    }

    private String resolveApiKey(String keyOrPath) {
        if (keyOrPath == null) return null;
        File keyFile = new File(keyOrPath);
        if (keyFile.exists() && keyFile.isFile()) {
            try {
                logger.debug("API key appears to be a file path, reading content from: " + keyFile.getAbsolutePath());
                return new String(Files.readAllBytes(keyFile.toPath()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read API key from file: " + keyOrPath, e);
            }
        }
        return keyOrPath;
    }

    public String getApiKey() {
        if (this.resolvedApiKey == null) {
            String keyOrPath = githubExtension.getAccessToken();
            this.resolvedApiKey = resolveApiKey(keyOrPath);
        }
        return this.resolvedApiKey;
    }

    public String getResourceRepoName() {
        String repoUrl = resourcesExtension.getRepoUrl();
        if (repoUrl == null || repoUrl.trim().isEmpty()) {
            return null;
        }
        String[] repoParts = repoUrl.split("/");
        return repoParts.length > 3 ? repoParts[3] : null;
    }

    public String getResourceRepoOwner() {
        String repoUrl = resourcesExtension.getRepoUrl();
        if (repoUrl == null || repoUrl.trim().isEmpty()) {
            return null;
        }
        String[] repoParts = repoUrl.split("/");
        return repoParts.length > 4 ? repoParts[4] : null;
    }

    private boolean isSshKey(String key) {
        return key != null && key.contains("-----BEGIN") && key.contains("PRIVATE KEY");
    }

    public CredentialsProvider getCredentialsProvider(String repoOwner) {
        String apiKey = getApiKey();
        if (apiKey == null) {
            return null;
        } else if (apiKey.startsWith("ghp_") || apiKey.startsWith("github_pat_")) {
            return new UsernamePasswordCredentialsProvider(repoOwner, apiKey);
        } else if (isSshKey(apiKey)) {
            return null;
        }
        throw new RuntimeException("Invalid API key format.");
    }

    private TransportConfigCallback getTransportConfigCallback() {
        String apiKey = getApiKey();
        if (isSshKey(apiKey)) {
            return new SshTransportConfigCallback(apiKey);
        }
        return null;
    }

    public void cloneRepository(File path, String repoOwner, String repoName) throws GitAPIException {
        String repositoryURL = "https://github.com/" + repoOwner + "/" + repoName;
        logger.log("Cloning repository... (" + repositoryURL + ")");
        try (Git ignored = Git.cloneRepository()
                .setURI(repositoryURL)
                .setCredentialsProvider(getCredentialsProvider(repoOwner))
                .setTransportConfigCallback(getTransportConfigCallback())
                .setDirectory(path)
                .call()) {
            logger.log("Repository cloned successfully.");
        }
    }

    public void cloneRepository(File path) throws GitAPIException {
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("resourcesExtension.repoUrl is not configured.");
        }
        cloneRepository(path, repoOwner, repoName);
    }

    public boolean doesRepoExist(File path) {
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

    public boolean isRepoUpToDate(File path) {
        String repoOwner = getResourceRepoOwner();
        if (repoOwner == null) {
            throw new IllegalStateException("Cannot determine repository owner because resourcesExtension.repoUrl is not configured.");
        }
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (
                Repository repository = builder.setGitDir(path.toPath().resolve(".git").toFile())
                        .readEnvironment()
                        .findGitDir()
                        .build();
                Git git = new Git(repository)
        ) {
            git.fetch().setCredentialsProvider(getCredentialsProvider(repoOwner)).setTransportConfigCallback(getTransportConfigCallback()).call();
            String branch = repository.getBranch();
            ObjectId localCommit = repository.resolve("refs/heads/" + branch);
            ObjectId remoteCommit = repository.resolve("refs/remotes/origin/" + branch);
            return localCommit != null && localCommit.equals(remoteCommit);
        } catch (IOException | GitAPIException exception) {
            return false;
        }
    }

    public void pullRepository(File path, String branch) throws GitAPIException, IOException {
        String repoOwner = getResourceRepoOwner();
        if (repoOwner == null) {
            throw new IllegalStateException("Cannot determine repository owner because resourcesExtension.repoUrl is not configured.");
        }
        try (Git repo = Git.open(path)) {
            Repository repository = repo.getRepository();
            Git git = new Git(repository);
            git.fetch().setCredentialsProvider(getCredentialsProvider(repoOwner)).setTransportConfigCallback(getTransportConfigCallback()).call();

            if (branch == null) {
                branch = repository.getBranch();
            }

            PullCommand pullCmd = git.pull()
                    .setCredentialsProvider(getCredentialsProvider(repoOwner))
                    .setTransportConfigCallback(getTransportConfigCallback())
                    .setRemoteBranchName(branch);

            logger.log("Pulling Repository branch " + branch);
            PullResult result = pullCmd.call();
            if (!result.isSuccessful()) {
                logger.error("Pull failed: " + branch);
            } else {
                logger.log("Successfully pulled repository.");
            }
        }
    }

    public void pullRepository(File path) throws GitAPIException, IOException {
        pullRepository(path, null);
    }

    public void cloneOrPullRepository(File path, String repoOwner, String repoName, String branch) throws GitAPIException, IOException {
        if (doesRepoExist(path)) {
            if (!isRepoUpToDate(path))
                pullRepository(path, branch);
            else {
                logger.log("Repository is up to date.");
            }
        } else {
            cloneRepository(path, repoOwner, repoName);
        }
    }

    public void cloneOrPullRepository(File path, String branch) throws GitAPIException, IOException {
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("resourcesExtension.repoUrl is not configured.");
        }
        cloneOrPullRepository(path, repoOwner, repoName, branch);
    }

    public void cloneOrPullRepository(File path) throws GitAPIException, IOException {
        cloneOrPullRepository(path, null);
    }

    public File getAsset(String repoOwner, String repoName, String version) {
        org.kohsuke.github.GitHub github = getGitHub();
        File direction = new File(GradleUtils.getGradleHome().resolve("github").toFile(), repoOwner);

        if (!direction.exists() && !direction.mkdirs())
            throw new RuntimeException("Failed to create directory: " + direction.getAbsolutePath());

        File jar = new File(direction, repoName + "-" + version + ".jar");

        logger.debug("Starting the process to implement jar: " + jar.getName());
        if (!jar.exists()) {
            try {
                GHRelease targetRelease = github.getRepository(repoOwner + "/" + repoName).getReleaseByTagName(version);

                if (targetRelease != null) {
                    for (GHAsset asset : targetRelease.listAssets()) {
                        if (asset.getName().equals(repoName + ".jar")) {
                            downloadAsset(jar, asset, repoOwner, repoName);
                            return jar;
                        }
                    }
                    throw new RuntimeException("No matching asset found for the release for " + repoOwner + ":" + repoName);
                } else {
                    throw new RuntimeException("Release not found for " + repoOwner + ":" + repoName);
                }
            } catch (IOException e) {
                throw new RuntimeException("GitHub exception while pulling asset: " + e.getMessage(), e);
            }
        } else {
            logger.debug("Jar already exists: " + jar.getName());
            return jar;
        }
    }

    public File getAsset(String version) {
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("resourcesExtension.repoUrl is not configured.");
        }
        return getAsset(repoOwner, repoName, version);
    }

    public void downloadAsset(File direction, GHAsset asset, String repoOwner, String repoName) throws IOException {
        logger.log("Downloading dependency from " + repoOwner + "/" + repoName);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(asset.getBrowserDownloadUrl())
                .addHeader("Accept", "application/octet-stream")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to download asset: " + response);
            }
            byte[] bytes = response.body().bytes();
            try (FileOutputStream fos = new FileOutputStream(direction)) {
                fos.write(bytes);
            }
        }
        logger.log("Download completed for dependency " + repoOwner + "/" + repoName);
    }

    public GHRelease getLatestRelease(String repoOwner, String repoName) {
        try {
            logger.debug("Fetching latest release from GitHub " + repoOwner + "/" + repoName);
            return getGitHub().getRepository(repoOwner + "/" + repoName).getLatestRelease();
        } catch (IOException e) {
            logger.error("Error fetching releases: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public GHRelease getLatestRelease() {
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("resourcesExtension.repoUrl is not configured.");
        }
        return getLatestRelease(repoOwner, repoName);
    }

    public String getLatestVersion(String repoOwner, String repoName) {
        GHRelease latestRelease = getLatestRelease(repoOwner, repoName);
        return latestRelease != null ? latestRelease.getTagName() : null;
    }

    public String getLatestVersion() {
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("resourcesExtension.repoUrl is not configured.");
        }
        return getLatestVersion(repoOwner, repoName);
    }

    public org.kohsuke.github.GitHub getGitHub() {
        try {
            String apiKey = getApiKey();
            if (apiKey == null || isSshKey(apiKey)) {
                return org.kohsuke.github.GitHub.connectAnonymously();
            } else {
                return org.kohsuke.github.GitHub.connectUsingOAuth(apiKey);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SshTransportConfigCallback implements TransportConfigCallback {
        private final String privateKey;

        public SshTransportConfigCallback(String privateKey) {
            this.privateKey = privateKey;
        }

        @Override
        public void configure(Transport transport) {
            if (transport instanceof SshTransport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(new JschConfigSessionFactory() {
                    @Override
                    protected void configure(OpenSshConfig.Host hc, Session session) {
                        session.setConfig("StrictHostKeyChecking", "no");
                    }

                    @Override
                    protected JSch createDefaultJSch(FS fs) throws JSchException {
                        JSch defaultJSch = super.createDefaultJSch(fs);
                        defaultJSch.addIdentity("deploy-key", privateKey.getBytes(), null, null);
                        return defaultJSch;
                    }
                });
            }
        }
    }
}