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
import org.eclipse.jgit.lib.Ref;
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
import java.util.List;

/**
 * Handles all the GitHub API related stuff.
 */
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
            if (keyOrPath == null) {
                return null;
            }
            this.resolvedApiKey = resolveApiKey(keyOrPath);
        }
        return this.resolvedApiKey;
    }

    public String getRepoName() {
        String[] repoParts = resourcesExtension.getRepoUrl().split("/");
        return repoParts[3];
    }

    public String getRepoOwner() {
        String[] repoParts = resourcesExtension.getRepoUrl().split("/");
        return repoParts[4];
    }

    private boolean isSshKey(String key) {
        return key != null && key.contains("-----BEGIN") && key.contains("PRIVATE KEY");
    }

    public CredentialsProvider getCredentialsProvider() {
        String apiKey = getApiKey();
        if (apiKey == null) {
            return null;
        } else if (
                apiKey.startsWith("ghp_") ||
                        apiKey.startsWith("github_pat_")
        ) {
            return new UsernamePasswordCredentialsProvider(getRepoOwner(), getApiKey());
        } else if (isSshKey(apiKey)) {
            return null;
        }
        throw new RuntimeException("Invalid API key format: " + apiKey);
    }

    private TransportConfigCallback getTransportConfigCallback() {
        String apiKey = getApiKey();
        if (isSshKey(apiKey)) {
            return new SshTransportConfigCallback(apiKey);
        }
        return null;
    }


    public void cloneRepository(File path) throws GitAPIException {
        String repositoryURL = "https://github.com/" + getRepoOwner() + "/" + getRepoName();
        logger.log("Cloning repository... (" + repositoryURL + ")");
        try (Git ignored = Git.cloneRepository()
                .setURI(repositoryURL)
                .setCredentialsProvider(getCredentialsProvider())
                .setTransportConfigCallback(getTransportConfigCallback())
                .setDirectory(path)
                .call()) {
            logger.log("Repository cloned successfully.");
        }
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
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (
                Repository repository = builder.setGitDir(path.toPath().resolve(".git").toFile())
                        .readEnvironment()
                        .findGitDir()
                        .build();
                Git git = new Git(repository)
        ) {
            git.fetch().setCredentialsProvider(getCredentialsProvider()).setTransportConfigCallback(getTransportConfigCallback()).call();
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

    public void pullRepository(File path) throws GitAPIException, IOException {
        pullRepository(path, null);
    }

    public void pullRepository(File path, String branch) throws GitAPIException, IOException {
        CredentialsProvider credentialsProvider = getCredentialsProvider();
        TransportConfigCallback transportConfigCallback = getTransportConfigCallback();

        try (Git repo = Git.open(path)) {
            Repository repository = repo.getRepository();
            Git git = new Git(repository);
            git.fetch().setCredentialsProvider(credentialsProvider).setTransportConfigCallback(transportConfigCallback).call();

            if (branch == null) {
                List<Ref> branches = git.branchList().call();
                if (branches.size() > 1) {
                    logger.warn("Repository has multiple branches, might pull wrong branch...");
                    for (Ref branch2 : branches) {
                        logger.warn("Branch: " + branch2.getName());
                    }
                }
                branch = branches.get(0).getName();
            }

            PullCommand pullCmd = git.pull()
                    .setCredentialsProvider(credentialsProvider)
                    .setTransportConfigCallback(transportConfigCallback)
                    .setRemoteBranchName(branch);

            logger.log("Pulling Repository branch" + branch);
            PullResult result = pullCmd.call();
            if (!result.isSuccessful()) {
                logger.error("Pull failed: " + branch);
            } else {
                logger.log("Successfully pulled repository.");
            }
        }
    }

    public void cloneOrPullRepository(File path) throws GitAPIException, IOException {
        cloneOrPullRepository(path, null);
    }

    public void cloneOrPullRepository(File path, String branch) throws GitAPIException, IOException {
        if (doesRepoExist(path)) {
            if (!isRepoUpToDate(path))
                pullRepository(path, branch);
            else {
                logger.log("Repository is up to date.");
            }
        } else {
            cloneRepository(path);
        }
    }

    public File getAsset(String version) {
        org.kohsuke.github.GitHub github = getGitHub();
        File direction = new File(GradleUtils.getGradleHome().resolve("github").toFile(), getRepoOwner());

        if (!direction.exists() && !direction.mkdirs())
            throw new RuntimeException("Failed to create directory: " + direction.getAbsolutePath());

        File jar = new File(direction, getRepoName() + "-" + version + ".jar");

        logger.debug("Starting the process to implement jar: " + jar.getName());
        if (!jar.exists()) {
            try {
                List<GHRelease> releases = github.getRepository(getRepoOwner() + "/" + getRepoName()).listReleases().toList();
                GHRelease targetRelease = null;
                for (GHRelease release : releases) {
                    if (release.getTagName().equals(version))
                        targetRelease = release;
                }
                if (targetRelease != null) {
                    List<GHAsset> assets = targetRelease.listAssets().toList();
                    if (!assets.isEmpty()) {
                        for (GHAsset asset : assets) {
                            if (asset.getName().equals(getRepoName() + ".jar")) {
                                downloadAsset(jar, asset);
                                return jar;
                            }
                        }
                    } else {
                        throw new RuntimeException("No assets found for the release for " + getRepoOwner() + ":" + getRepoName());
                    }
                } else {
                    throw new RuntimeException("Release not found for " + getRepoOwner() + ":" + getRepoName());
                }
            } catch (IOException e) {
                throw new RuntimeException("Github exception while pulling asset: " + e.getMessage() + " (retrying in 5 seconds...)");
            }
            throw new RuntimeException("Could not find an valid asset for " + getRepoOwner() + ":" + getRepoName());
        } else {
            logger.debug("Jar already exists: " + jar.getName());
            return jar;
        }
    }

    public void downloadAsset(File direction, GHAsset asset) throws IOException {
        logger.log("Downloading dependency from " + getRepoOwner() + "/" + getRepoName());
        String downloadUrl = "https://api.github.com/repos/" + getRepoOwner() + "/" + getRepoName() + "/releases/assets/" + asset.getId();
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
        logger.log("Download completed for dependency " + getRepoOwner() + "/" + getRepoName());
    }

    public GHRelease getLatestRelease() {
        org.kohsuke.github.GitHub github = getGitHub();
        try {
            logger.debug("Fetching releases from GitHub " + getRepoOwner() + "/" + getRepoName());
            List<GHRelease> releases = github.getRepository(getRepoOwner() + "/" + getRepoName()).listReleases().toList();
            logger.debug("Found releases for " + getRepoName() + ": " + releases);
            if (!releases.isEmpty()) {
                logger.debug("Latest release found for " + getRepoName() + ": " + releases.get(0).getTagName());
                return releases.get(0);
            } else
                throw new RuntimeException("No releases found for " + getRepoName());
        } catch (IOException e) {
            logger.error("Error fetching releases: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public String getLatestVersion() {
        GHRelease latestRelease = getLatestRelease();
        return latestRelease.getTagName();
    }



    public org.kohsuke.github.GitHub getGitHub() {
        org.kohsuke.github.GitHub github;
        try {
            if (getApiKey() == null) {
                github = org.kohsuke.github.GitHub.connectAnonymously();
                logger.debug("Pulling from github anonymously");
            } else {
                github = org.kohsuke.github.GitHub.connectUsingOAuth(getApiKey());
                logger.debug("Pulling from github using OAuth");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return github;
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