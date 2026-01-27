package io.github.intisy.gradle.github.impl;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.Logger;
import io.github.intisy.gradle.github.extension.ResourcesExtension;
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
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

/**
 * GitHub helper class for managing GitHub repositories, releases, and assets.
 * Provides methods for cloning, pulling, and fetching repository information.
 */
@SuppressWarnings("unused")
public class GitHub {
    private final Logger logger;
    private final ResourcesExtension resourcesExtension;
    private final GithubExtension githubExtension;
    private String resolvedApiKey;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * Constructs a new GitHub helper instance.
     *
     * @param logger the logger instance for debug and error messages
     * @param resourcesExtension the resources extension containing repository configuration
     * @param githubExtension the github extension containing access token configuration
     */
    public GitHub(Logger logger, ResourcesExtension resourcesExtension, GithubExtension githubExtension) {
        this.logger = logger;
        this.resourcesExtension = resourcesExtension;
        this.githubExtension = githubExtension;
        this.resolvedApiKey = null;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        logger.debug("GitHub helper initialized.");
    }

    /**
     * Checks if a string looks like a file path (even if the file doesn't exist).
     *
     * @param value the string to check
     * @return true if the string appears to be a file path
     */
    private boolean looksLikeFilePath(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        if (value.contains("/") || value.contains("\\")) {
            return true;
        }

        if (value.matches(".*\\.[a-zA-Z0-9]{1,10}$")) {
            return true;
        }

        try {
            Paths.get(value);
            File f = new File(value);
            return f.isAbsolute() || value.contains(File.separator);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Resolves an API key from either a direct value or a file path.
     *
     * @param keyOrPath the API key value or path to a file containing the key
     * @return the resolved API key, or null if input is null
     * @throws RuntimeException if the key appears to be a file path but the file doesn't exist or can't be read
     */
    private String resolveApiKey(String keyOrPath) {
        logger.debug("Resolving API key...");
        if (keyOrPath == null) {
            logger.debug("API key is null.");
            return null;
        }

        if (looksLikeFilePath(keyOrPath)) {
            File keyFile = new File(keyOrPath);
            if (keyFile.exists() && keyFile.isFile()) {
                try {
                    logger.debug("API key appears to be a file path, reading content from: " + keyFile.getAbsolutePath());
                    String keyContent = new String(Files.readAllBytes(keyFile.toPath())).trim();
                    logger.debug("Successfully read API key from file.");
                    return keyContent;
                } catch (IOException e) {
                    logger.error("Failed to read API key from file: " + keyOrPath, e);
                    throw new RuntimeException("Failed to read API key from file: " + keyOrPath, e);
                }
            } else {
                logger.error("API key appears to be a file path, but the file does not exist: " + keyOrPath);
                throw new RuntimeException("API key file does not exist: " + keyOrPath);
            }
        } else {
            logger.debug("API key is not a file path, using value directly.");
        }
        return keyOrPath;
    }

    /**
     * Gets the GitHub API key, resolving it from a file if necessary.
     * The key is cached after the first resolution.
     *
     * @return the resolved API key, or null if not configured
     */
    public String getApiKey() {
        if (this.resolvedApiKey == null) {
            logger.debug("API key not cached, resolving from extension.");
            String keyOrPath = githubExtension.getAccessToken();
            this.resolvedApiKey = resolveApiKey(keyOrPath);
        }
        return this.resolvedApiKey;
    }

    /**
     * Extracts the repository name from the configured repository URL.
     *
     * @return the repository name, or null if not configured
     */
    public String getResourceRepoName() {
        String repoUrl = resourcesExtension.getRepoUrl();
        logger.debug("Reading repoUrl from resourcesExtension: '" + repoUrl + "'");
        if (repoUrl == null || repoUrl.trim().isEmpty()) {
            logger.debug("Variable resourcesExtension.repoUrl is null or empty.");
            return null;
        }
        String[] repoParts = repoUrl.split("/");
        String lastPart = repoParts[repoParts.length - 1];
        String repoName = lastPart.endsWith(".git") ? lastPart.substring(0, lastPart.length() - 4) : lastPart;
        logger.debug("Parsed repository name: '" + repoName + "'");
        return repoName;
    }

    /**
     * Extracts the repository owner from the configured repository URL.
     *
     * @return the repository owner, or null if not configured
     */
    public String getResourceRepoOwner() {
        String repoUrl = resourcesExtension.getRepoUrl();
        logger.debug("Reading repoUrl from resourcesExtension: '" + repoUrl + "'");
        if (repoUrl == null || repoUrl.trim().isEmpty()) {
            logger.debug("repoUrl is null or empty.");
            return null;
        }
        String[] repoParts = repoUrl.split("/");
        String repoOwner = repoParts.length > 3 ? repoParts[3] : null;
        if (repoUrl.startsWith("git@")) {
            String partAfterColon = repoUrl.split(":")[1];
            repoOwner = partAfterColon.split("/")[0];
        }
        logger.debug("Parsed repository owner: '" + repoOwner + "'");
        return repoOwner;
    }

    /**
     * Checks if a string is an SSH private key.
     *
     * @param key the string to check
     * @return true if the key appears to be an SSH private key
     */
    private boolean isSshKey(String key) {
        boolean isSsh = key != null && key.contains("-----BEGIN") && key.contains("PRIVATE KEY");
        logger.debug("Checking if key is SSH private key... Result: " + isSsh);
        return isSsh;
    }

    /**
     * Checks if a string is a GitHub Personal Access Token.
     *
     * @param key the string to check
     * @return true if the key is a GitHub PAT
     */
    private boolean isGitHubPAT(String key) {
        boolean isPAT = key != null && (key.startsWith("ghp_") || key.startsWith("github_pat_"));
        logger.debug("Checking if key is GitHub PAT... Result: " + isPAT);
        return isPAT;
    }

    /**
     * Creates a credentials provider for Git operations.
     *
     * @param repoOwner the repository owner for authentication
     * @return the credentials provider, or null if SSH authentication is used
     */
    public CredentialsProvider getCredentialsProvider(String repoOwner) {
        logger.debug("Attempting to get CredentialsProvider for owner: " + repoOwner);
        String apiKey = getApiKey();
        if (apiKey == null) {
            logger.debug("No API key provided. Returning null CredentialsProvider.");
            return null;
        } else if (isGitHubPAT(apiKey)) {
            logger.debug("API key is a GitHub PAT. Creating UsernamePasswordCredentialsProvider.");
            return new UsernamePasswordCredentialsProvider(repoOwner, apiKey);
        } else if (isSshKey(apiKey)) {
            logger.debug("API key is an SSH key. Returning null CredentialsProvider (should be handled by TransportConfigCallback).");
            return null;
        }
        logger.error("API key format is invalid. It is not a PAT or a valid private key.");
        throw new RuntimeException("Invalid API key format.");
    }

    /**
     * Creates a transport configuration callback for SSH authentication.
     *
     * @return the transport configuration callback, or null if SSH is not used
     */
    private TransportConfigCallback getTransportConfigCallback() {
        logger.debug("Attempting to get TransportConfigCallback.");
        String apiKey = getApiKey();
        if (isSshKey(apiKey)) {
            logger.debug("API key is an SSH key. Creating SshTransportConfigCallback.");
            return new TransportConfigCallback() {
                @Override
                public void configure(Transport transport) {
                    if (transport instanceof SshTransport) {
                        logger.debug("Configuring SshTransport with custom JschConfigSessionFactory.");
                        SshTransport sshTransport = (SshTransport) transport;
                        sshTransport.setSshSessionFactory(new JschConfigSessionFactory() {
                            @Override
                            protected void configure(OpenSshConfig.Host hc, Session session) {
                                logger.debug("Jsch session configure: Disabling StrictHostKeyChecking.");
                                session.setConfig("StrictHostKeyChecking", "no");
                            }

                            @Override
                            protected JSch createDefaultJSch(FS fs) throws JSchException {
                                logger.debug("Jsch createDefaultJSch: Adding private key identity 'deploy-key'.");
                                JSch defaultJSch = super.createDefaultJSch(fs);
                                defaultJSch.addIdentity("deploy-key", apiKey.getBytes(), null, null);
                                return defaultJSch;
                            }
                        });
                    }
                }
            };
        }
        logger.debug("No SSH key provided. Returning null TransportConfigCallback.");
        return null;
    }

    /**
     * Constructs the appropriate Git repository URL based on authentication type.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @return the Git repository URL (SSH or HTTPS)
     */
    private String getRepositoryURL(String repoOwner, String repoName) {
        if (isSshKey(getApiKey())) {
            String url = String.format("git@github.com:%s/%s.git", repoOwner, repoName);
            logger.debug("Detected SSH key, using SSH URL for Git operations: " + url);
            return url;
        }
        String url = String.format("https://github.com/%s/%s", repoOwner, repoName);
        logger.debug("Using HTTPS URL for Git operations: " + url);
        return url;
    }

    /**
     * Clones a GitHub repository to the specified path.
     *
     * @param path the directory to clone the repository into
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @throws GitAPIException if the clone operation fails
     */
    public void cloneRepository(File path, String repoOwner, String repoName) throws GitAPIException {
        String repositoryURL = getRepositoryURL(repoOwner, repoName);
        logger.log("Cloning repository... (" + repositoryURL + ") into " + path.getAbsolutePath());
        try (Git ignored = Git.cloneRepository()
                .setURI(repositoryURL)
                .setCredentialsProvider(getCredentialsProvider(repoOwner))
                .setTransportConfigCallback(getTransportConfigCallback())
                .setDirectory(path)
                .call()) {
            logger.log("Repository cloned successfully.");
        } catch (GitAPIException e) {
            logger.error("Failed to clone repository: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Clones the configured resource repository to the specified path.
     *
     * @param path the directory to clone the repository into
     * @throws GitAPIException if the clone operation fails
     */
    public void cloneRepository(File path) throws GitAPIException {
        logger.debug("Method cloneRepository called without owner/name, using resourcesExtension.");
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("Variable resourcesExtension.repoUrl is not configured properly.");
        }
        cloneRepository(path, repoOwner, repoName);
    }

    /**
     * Checks if a Git repository exists at the specified path.
     *
     * @param path the directory to check
     * @return true if a repository exists, false otherwise
     */
    public boolean doesRepoExist(File path) {
        logger.debug("Checking if repository exists at: " + path.getAbsolutePath());
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(path.toPath().resolve(".git").toFile())
                .readEnvironment()
                .findGitDir()
                .build()) {
            boolean exists = repository.getObjectDatabase().exists();
            logger.debug("Repository existence check result: " + exists);
            return exists;
        } catch (IOException e) {
            logger.debug("IOException while checking for repository existence. Assuming it doesn't exist.");
            return false;
        }
    }

    /**
     * Checks if the local repository is up-to-date with the remote.
     *
     * @param path the repository directory
     * @return true if up-to-date, false otherwise
     */
    public boolean isRepoUpToDate(File path) {
        logger.debug("Checking if repository is up-to-date at: " + path.getAbsolutePath());
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
            logger.debug("Performing git fetch...");
            git.fetch().setCredentialsProvider(getCredentialsProvider(repoOwner)).setTransportConfigCallback(getTransportConfigCallback()).call();
            logger.debug("Fetch completed.");
            String branch = repository.getBranch();
            ObjectId localCommit = repository.resolve("refs/heads/" + branch);
            ObjectId remoteCommit = repository.resolve("refs/remotes/origin/" + branch);
            logger.debug("Local commit for branch '" + branch + "': " + (localCommit != null ? localCommit.getName() : "null"));
            logger.debug("Remote commit for branch 'origin/" + branch + "': " + (remoteCommit != null ? remoteCommit.getName() : "null"));
            boolean upToDate = localCommit != null && localCommit.equals(remoteCommit);
            logger.debug("Repository up-to-date check result: " + upToDate);
            return upToDate;
        } catch (IOException | GitAPIException exception) {
            logger.error("Exception during isRepoUpToDate check, returning false.", exception);
            return false;
        }
    }

    /**
     * Pulls the latest changes from the remote repository.
     *
     * @param path the repository directory
     * @param branch the branch to pull, or null for the current branch
     * @throws GitAPIException if the pull operation fails
     * @throws IOException if an I/O error occurs
     */
    public void pullRepository(File path, String branch) throws GitAPIException, IOException {
        logger.debug("Attempting to pull repository at " + path.getAbsolutePath());
        String repoOwner = getResourceRepoOwner();
        if (repoOwner == null) {
            throw new IllegalStateException("Cannot determine repository owner because resourcesExtension.repoUrl is not configured.");
        }
        try (Git repo = Git.open(path)) {
            Repository repository = repo.getRepository();
            Git git = new Git(repository);
            logger.debug("Performing git fetch before pull...");
            git.fetch().setCredentialsProvider(getCredentialsProvider(repoOwner)).setTransportConfigCallback(getTransportConfigCallback()).call();
            logger.debug("Fetch completed.");

            if (branch == null) {
                branch = repository.getBranch();
                logger.debug("Branch not specified, using current branch: " + branch);
            }

            PullCommand pullCmd = git.pull()
                    .setCredentialsProvider(getCredentialsProvider(repoOwner))
                    .setTransportConfigCallback(getTransportConfigCallback())
                    .setRemoteBranchName(branch);

            logger.log("Pulling Repository branch " + branch);
            PullResult result = pullCmd.call();
            logger.debug("Pull result successful: " + result.isSuccessful());
            if (!result.isSuccessful()) {
                logger.error("Pull failed: " + branch + ". Merge status: " + result.getMergeResult().getMergeStatus());
            } else {
                logger.log("Successfully pulled repository.");
            }
        } catch (GitAPIException | IOException e) {
            logger.error("Exception during pullRepository: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Pulls the latest changes from the current branch of the remote repository.
     *
     * @param path the repository directory
     * @throws GitAPIException if the pull operation fails
     * @throws IOException if an I/O error occurs
     */
    public void pullRepository(File path) throws GitAPIException, IOException {
        pullRepository(path, null);
    }

    /**
     * Clones a repository if it doesn't exist, otherwise pulls the latest changes.
     *
     * @param path the repository directory
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param branch the branch to pull, or null for the current branch
     * @throws GitAPIException if the clone or pull operation fails
     * @throws IOException if an I/O error occurs
     */
    public void cloneOrPullRepository(File path, String repoOwner, String repoName, String branch) throws GitAPIException, IOException {
        logger.debug("Executing cloneOrPull for " + repoOwner + "/" + repoName + " at " + path.getAbsolutePath());
        if (doesRepoExist(path)) {
            logger.debug("Repository exists, checking if it's up-to-date.");
            if (!isRepoUpToDate(path)) {
                logger.debug("Repository not up-to-date, pulling...");
                pullRepository(path, branch);
            } else {
                logger.log("Repository is up to date.");
                ensureCorrectBranch(path, branch);
            }
        } else {
            logger.debug("Repository does not exist, cloning...");
            cloneRepository(path, repoOwner, repoName);
        }
    }

    /**
     * Ensures that the given Git repository is on the specified branch. If the current branch
     * does not match the specified branch, it will attempt to check out the desired branch.
     *
     * @param path the file path to the Git repository
     * @param branch the desired branch to ensure is checked out; if null, no action is taken
     * @throws IOException if an I/O error occurs while accessing the repository
     * @throws GitAPIException if a Git-specific error occurs during operations such as checkout
     */
    private void ensureCorrectBranch(File path, String branch) throws IOException, GitAPIException {
        if (branch == null) {
            return;
        }
        try (Git git = Git.open(path)) {
            String currentBranch = git.getRepository().getBranch();
            if (!currentBranch.equals(branch)) {
                logger.log("Current branch '" + currentBranch + "' is not the desired branch '" + branch + "'. Checking out '" + branch + "'...");

                boolean branchExists = git.branchList().call().stream()
                        .anyMatch(ref -> ref.getName().equals("refs/heads/" + branch));

                if (branchExists) {
                    git.checkout().setName(branch).call();
                } else {
                    git.checkout()
                            .setCreateBranch(true)
                            .setName(branch)
                            .setStartPoint("origin/" + branch)
                            .call();
                }
                logger.log("Successfully checked out branch '" + branch + "'.");
            }
        } catch (IOException | GitAPIException e) {
            logger.error("Failed to checkout branch '" + branch + "': " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Clones the configured resource repository if it doesn't exist, otherwise pulls the latest changes.
     *
     * @param path the repository directory
     * @param branch the branch to pull, or null for the current branch
     * @throws GitAPIException if the clone or pull operation fails
     * @throws IOException if an I/O error occurs
     */
    public void cloneOrPullRepository(File path, String branch) throws GitAPIException, IOException {
        logger.debug("Method cloneOrPullRepository called without owner/name, using resourcesExtension.");
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("Variable resourcesExtension.repoUrl is not configured.");
        }
        cloneOrPullRepository(path, repoOwner, repoName, branch);
    }

    /**
     * Clones the configured resource repository if it doesn't exist, otherwise pulls the latest changes from the current branch.
     *
     * @param path the repository directory
     * @throws GitAPIException if the clone or pull operation fails
     * @throws IOException if an I/O error occurs
     */
    public void cloneOrPullRepository(File path) throws GitAPIException, IOException {
        cloneOrPullRepository(path, null);
    }

    /**
     * Makes an authenticated GitHub API request.
     *
     * @param url the API URL to request
     * @return the response object
     * @throws IOException if the request fails
     */
    private Response makeGitHubApiRequest(String url) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28");

        String apiKey = getApiKey();
        if (apiKey != null && isGitHubPAT(apiKey)) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        return httpClient.newCall(requestBuilder.build()).execute();
    }

    /**
     * Downloads and caches a release asset JAR file from a GitHub repository.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param version the release version tag
     * @return the downloaded JAR file
     */
    public File getAsset(String repoOwner, String repoName, String version) {
        logger.debug("Attempting to get asset for " + repoOwner + "/" + repoName + " version " + version);
        File direction = new File(GradleUtils.getGradleHome().resolve("github").toFile(), repoOwner);
        logger.debug("Asset cache directory: " + direction.getAbsolutePath());

        if (!direction.exists()) {
            logger.debug("Cache directory does not exist, creating it.");
            if (!direction.mkdirs()) {
                throw new RuntimeException("Failed to create directory: " + direction.getAbsolutePath());
            }
        }

        File jar = new File(direction, repoName + "-" + version + ".jar");
        logger.debug("Expected asset file location: " + jar.getAbsolutePath());

        if (!jar.exists()) {
            logger.debug("Asset not found in cache. Fetching from GitHub API.");
            try {
                String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/tags/%s",
                        repoOwner, repoName, version);
                logger.debug("Fetching release from: " + apiUrl);

                try (Response response = makeGitHubApiRequest(apiUrl)) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new RuntimeException("Failed to fetch release: " + response.code() + " " + response.message());
                    }

                    JsonObject release = gson.fromJson(response.body().string(), JsonObject.class);
                    JsonArray assets = release.getAsJsonArray("assets");

                    if (assets == null || assets.isEmpty()) {
                        throw new RuntimeException("No assets found for release " + version);
                    }

                    for (int i = 0; i < assets.size(); i++) {
                        JsonObject asset = assets.get(i).getAsJsonObject();
                        String assetName = asset.get("name").getAsString();
                        logger.debug("Checking asset: '" + assetName + "'");

                        if (assetName.equals(repoName + ".jar")) {
                            logger.debug("Found matching asset. Downloading...");
                            String downloadUrl = asset.get("browser_download_url").getAsString();
                            downloadAssetFromUrl(jar, downloadUrl, repoOwner, repoName);
                            return jar;
                        }
                    }
                    throw new RuntimeException("No matching asset found for the release for " + repoOwner + ":" + repoName);
                }
            } catch (IOException e) {
                logger.error("IOException while getting asset: " + e.getMessage(), e);
                throw new RuntimeException("GitHub exception while pulling asset: " + e.getMessage(), e);
            }
        } else {
            logger.debug("Jar already exists in cache: " + jar.getName());
            return jar;
        }
    }

    /**
     * Downloads and caches a release asset JAR file from the configured resource repository.
     *
     * @param version the release version tag
     * @return the downloaded JAR file
     */
    public File getAsset(String version) {
        logger.debug("Method getAsset called without owner/name, using resourcesExtension.");
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("Variable resourcesExtension.repoUrl is not configured.");
        }
        return getAsset(repoOwner, repoName, version);
    }

    /**
     * Downloads a GitHub release asset from a URL to the specified file location.
     *
     * @param destination the destination file
     * @param downloadUrl the asset download URL
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @throws IOException if the download fails
     */
    private void downloadAssetFromUrl(File destination, String downloadUrl, String repoOwner, String repoName) throws IOException {
        logger.log("Downloading dependency from " + repoOwner + "/" + repoName);
        logger.debug("Asset download URL: " + downloadUrl);
        logger.debug("Destination file: " + destination.getAbsolutePath());

        Request request = new Request.Builder()
                .url(downloadUrl)
                .addHeader("Accept", "application/octet-stream")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.debug("HTTP response: " + response.code() + " " + response.message());
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to download asset: " + response);
            }
            byte[] bytes = response.body().bytes();
            logger.debug("Download size: " + bytes.length + " bytes.");
            try (FileOutputStream fos = new FileOutputStream(destination)) {
                fos.write(bytes);
            }
            logger.debug("Asset written to file successfully.");
        } catch (IOException e) {
            logger.error("IOException during asset download: " + e.getMessage(), e);
            throw e;
        }
        logger.log("Download completed for dependency " + repoOwner + "/" + repoName);
    }

    /**
     * Downloads a GitHub release asset to the specified file location.
     *
     * @deprecated Use downloadAssetFromUrl instead
     * @param direction the destination file
     * @param asset the GitHub asset object (no longer supported)
     * @param repoOwner the repository owner
     * @param repoName the repository name
     */
    @Deprecated
    public void downloadAsset(File direction, Object asset, String repoOwner, String repoName) {
        throw new UnsupportedOperationException("This method has been removed. Use REST API methods instead.");
    }

    /**
     * Fetches the latest release from a GitHub repository.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @return JSON object representing the latest release, or null if no releases exist
     */
    public JsonObject getLatestRelease(String repoOwner, String repoName) {
        logger.debug("Fetching latest release from GitHub API for " + repoOwner + "/" + repoName);
        try {
            String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest",
                    repoOwner, repoName);

            try (Response response = makeGitHubApiRequest(apiUrl)) {
                if (response.code() == 404) {
                    logger.debug("No releases found for " + repoOwner + "/" + repoName);
                    return null;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    throw new RuntimeException("Failed to fetch latest release: " + response.code() + " " + response.message());
                }

                JsonObject release = gson.fromJson(response.body().string(), JsonObject.class);
                logger.debug("Found latest release with tag: " + release.get("tag_name").getAsString());
                return release;
            }
        } catch (IOException e) {
            logger.error("Error fetching latest release: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetches the latest release from the configured resource repository.
     *
     * @return JSON object representing the latest release, or null if no releases exist
     */
    public JsonObject getLatestRelease() {
        logger.debug("Method getLatestRelease called without owner/name, using resourcesExtension.");
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("Variable resourcesExtension.repoUrl is not configured.");
        }
        return getLatestRelease(repoOwner, repoName);
    }

    /**
     * Gets the latest version tag from a GitHub repository.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @return the latest version tag, or null if no releases exist
     */
    public String getLatestVersion(String repoOwner, String repoName) {
        logger.debug("Getting latest version for " + repoOwner + "/" + repoName);
        JsonObject latestRelease = getLatestRelease(repoOwner, repoName);
        String version = latestRelease != null ? latestRelease.get("tag_name").getAsString() : null;
        logger.debug("Latest version resolved to: '" + version + "'");
        return version;
    }

    /**
     * Gets the latest version tag from the configured resource repository.
     *
     * @return the latest version tag, or null if no releases exist
     */
    public String getLatestVersion() {
        logger.debug("Method getLatestVersion called without owner/name, using resourcesExtension.");
        String repoOwner = getResourceRepoOwner();
        String repoName = getResourceRepoName();
        if (repoOwner == null || repoName == null) {
            throw new IllegalStateException("Variable resourcesExtension.repoUrl is not configured.");
        }
        return getLatestVersion(repoOwner, repoName);
    }
}
