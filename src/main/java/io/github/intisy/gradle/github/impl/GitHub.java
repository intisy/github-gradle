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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        return httpClient.newCall(requestBuilder.build()).execute();
    }

    /**
     * Builds a user-friendly error message for a failed GitHub API response.
     * Consumes the response body if present. Call only when the response is not successful.
     *
     * @param response the failed HTTP response (body will be consumed)
     * @param context  description of what was being requested (e.g. "release owner/repo tag v1.0")
     * @return a detailed error message including remediation hints
     */
    private String buildGitHubApiErrorMessage(Response response, String context) {
        int code = response.code();
        String statusMessage = response.message();
        String bodyMessage = null;
        if (response.body() != null) {
            try {
                String body = response.body().string();
                if (body != null && !body.isEmpty()) {
                    try {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        if (json.has("message")) {
                            bodyMessage = json.get("message").getAsString();
                        }
                    } catch (Exception e) {
                        bodyMessage = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                    }
                }
            } catch (IOException e) {
                // ignore when reading error body
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append("GitHub API request failed for ").append(context).append(". ");
        msg.append("HTTP ").append(code).append(" ").append(statusMessage);
        if (bodyMessage != null) {
            msg.append(" — ").append(bodyMessage);
        }
        msg.append(".\n\n");

        switch (code) {
            case 401:
                msg.append("FIX: Add a GitHub Personal Access Token so the plugin can access the API.\n");
                if (getApiKey() == null) {
                    msg.append("  • In build.gradle add: github { accessToken = \"ghp_YOUR_TOKEN\" }\n");
                    msg.append("  • Or set the GITHUB_TOKEN environment variable.\n");
                } else {
                    msg.append("  • Your token is set but was rejected. Check it is valid and not expired.\n");
                    msg.append("  • Create or regenerate a PAT at: https://github.com/settings/tokens\n");
                }
                msg.append("  • For public repos no scope is needed; for private repos enable the 'repo' scope.");
                break;
            case 403:
                if (bodyMessage != null && bodyMessage.toLowerCase().contains("rate limit")) {
                    msg.append("FIX: GitHub API rate limit exceeded (60/hr unauthenticated).\n");
                    msg.append("  • Add a token to get 5,000 requests/hour: github { accessToken = \"ghp_YOUR_TOKEN\" } in build.gradle\n");
                    msg.append("  • Or wait and retry later.");
                } else {
                    msg.append("FIX: Request forbidden — token may lack permission.\n");
                    msg.append("  • For private repositories, ensure your PAT has the 'repo' scope.\n");
                    msg.append("  • Update token at: https://github.com/settings/tokens");
                }
                break;
            case 404:
                msg.append("FIX: Repository or release not found.\n");
                msg.append("  • Check that the owner, repo name, and release tag are correct in your githubImplementation dependency.\n");
                msg.append("  • If the repo is private, ensure your token has access to it.");
                break;
            default:
                msg.append("FIX: Check your network and GitHub status. Retry with --info for more details.");
                break;
        }
        return msg.toString();
    }

    /**
     * Builds a user-friendly error message for a failed HTTP response when the body is not JSON
     * (e.g. asset download). Does not consume the response body.
     */
    private String buildHttpErrorMessage(int code, String statusMessage, String context) {
        StringBuilder msg = new StringBuilder();
        msg.append("Download failed for ").append(context).append(". HTTP ").append(code).append(" ").append(statusMessage).append(".\n\n");
        switch (code) {
            case 401:
                msg.append("FIX: Add a token so the plugin can download the asset: github { accessToken = \"ghp_YOUR_TOKEN\" } in build.gradle, or set GITHUB_TOKEN.");
                break;
            case 403:
                msg.append("FIX: If rate limited, add a token (github { accessToken = \"...\" }). If forbidden, ensure your PAT has the 'repo' scope for this repository.");
                break;
            case 404:
                msg.append("FIX: Check that the release and asset exist at the given tag. For private repos, ensure your token has access.");
                break;
            default:
                break;
        }
        return msg.toString();
    }

    /**
     * Attempts to fetch a GitHub release by tag, trying the given tag first and then
     * a "v"-prefixed or "v"-stripped variant as a fallback.
     *
     * @param repoOwner the repository owner
     * @param repoName  the repository name
     * @param version   the release version tag as declared by the consumer
     * @return the parsed release JSON object
     * @throws RuntimeException if neither tag variant resolves to a release
     */
    public JsonObject fetchReleaseByTag(String repoOwner, String repoName, String version) {
        String[] tagsToTry = version.startsWith("v")
                ? new String[]{version, version.substring(1)}
                : new String[]{version, "v" + version};

        for (String tag : tagsToTry) {
            String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/tags/%s",
                    repoOwner, repoName, tag);
            logger.debug("Trying release tag: " + tag);
            try (Response response = makeGitHubApiRequest(apiUrl)) {
                if (response.code() == 404) {
                    logger.debug("Tag '" + tag + "' not found, trying next variant.");
                    continue;
                }
                if (!response.isSuccessful()) {
                    String context = "release " + repoOwner + "/" + repoName + " tag " + tag;
                    throw new RuntimeException(buildGitHubApiErrorMessage(response, context));
                }
                if (response.body() == null) {
                    throw new RuntimeException("GitHub API returned empty body for release "
                            + repoOwner + "/" + repoName + " tag " + tag + ".");
                }
                return gson.fromJson(response.body().string(), JsonObject.class);
            } catch (IOException e) {
                logger.debug("IOException for tag '" + tag + "': " + e.getMessage());
            }
        }
        throw new RuntimeException("No release found for " + repoOwner + "/" + repoName
                + " with tag '" + version + "' or '" + tagsToTry[1] + "'.");
    }

    /**
     * Selects the best JAR asset from a release using a prioritized matching strategy:
     * (1) exact {@code repoName.jar}, (2) {@code repoName-version.jar},
     * (3) {@code repoName-standalone.jar}, (4) first {@code .jar} not ending in
     * {@code -sources.jar} or {@code -javadoc.jar}.
     *
     * @param assets   the release assets JSON array
     * @param repoName the repository name
     * @param version  the release version tag
     * @return the selected asset JSON object, or null if no suitable JAR found
     */
    public JsonObject selectJarAsset(JsonArray assets, String repoName, String version) {
        JsonObject fallback = null;
        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            String name = asset.get("name").getAsString();
            logger.debug("Checking asset: '" + name + "'");
            if (name.equals(repoName + ".jar")) {
                logger.debug("Matched exact: " + name);
                return asset;
            }
            if (name.equals(repoName + "-" + version + ".jar")) {
                logger.debug("Matched versioned: " + name);
                return asset;
            }
            if (name.equals(repoName + "-standalone.jar")) {
                logger.debug("Matched standalone: " + name);
                return asset;
            }
            if (fallback == null && name.endsWith(".jar")
                    && !name.endsWith("-sources.jar")
                    && !name.endsWith("-javadoc.jar")) {
                fallback = asset;
            }
        }
        if (fallback != null) {
            logger.debug("Using fallback JAR: " + fallback.get("name").getAsString());
        }
        return fallback;
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
            JsonObject release = fetchReleaseByTag(repoOwner, repoName, version);
            JsonArray assets = release.getAsJsonArray("assets");

            if (assets == null || assets.isEmpty()) {
                throw new RuntimeException("No assets found for release " + version);
            }

            JsonObject selected = selectJarAsset(assets, repoName, version);
            if (selected == null) {
                throw new RuntimeException("No matching JAR asset found for " + repoOwner + ":" + repoName
                        + ". Available assets don't include a .jar file (excluding -sources.jar and -javadoc.jar).");
            }

            String downloadUrl = selected.get("browser_download_url").getAsString();
            try {
                downloadAssetFromUrl(jar, downloadUrl, repoOwner, repoName);
            } catch (IOException e) {
                throw new RuntimeException("Failed to download asset from " + downloadUrl + ": " + e.getMessage(), e);
            }
            return jar;
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
        long startTime = System.currentTimeMillis();
        logger.debug("Asset download URL: " + downloadUrl);
        logger.debug("Destination file: " + destination.getAbsolutePath());

        Request.Builder assetRequestBuilder = new Request.Builder()
                .url(downloadUrl)
                .addHeader("Accept", "application/octet-stream");
        String downloadApiKey = getApiKey();
        if (downloadApiKey != null && !downloadApiKey.trim().isEmpty()) {
            assetRequestBuilder.addHeader("Authorization", "Bearer " + downloadApiKey);
        }
        Request request = assetRequestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            logger.debug("HTTP response: " + response.code() + " " + response.message());
            if (!response.isSuccessful()) {
                String context = repoOwner + "/" + repoName;
                throw new IOException(buildHttpErrorMessage(response.code(), response.message(), context));
            }
            if (response.body() == null) {
                throw new IOException("Empty response body when downloading " + repoOwner + "/" + repoName + ".");
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
        logger.log("Download " + downloadUrl + ", took " + (System.currentTimeMillis() - startTime) + " ms");
    }

    /**
     * Reads the embedded github-dependencies metadata from a JAR file.
     * The metadata is stored at {@code META-INF/github-dependencies.json} and contains
     * a JSON array of objects with group, name, and version fields. This location is
     * safe from obfuscation tools (ProGuard, R8, etc.) which only process class files.
     *
     * @param jar the JAR file to read metadata from
     * @return a list of dependency entries as [group, name, version] arrays, empty if no metadata found
     */
    public List<String[]> readGithubDependencies(File jar) {
        List<String[]> dependencies = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(jar)) {
            ZipEntry entry = zipFile.getEntry("META-INF/github-dependencies.json");
            if (entry == null) {
                logger.debug("No github-dependencies.json found in " + jar.getName());
                return dependencies;
            }
            try (InputStream is = zipFile.getInputStream(entry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                JsonArray array = gson.fromJson(content.toString(), JsonArray.class);
                for (int i = 0; i < array.size(); i++) {
                    JsonObject dep = array.get(i).getAsJsonObject();
                    String group = dep.get("group").getAsString();
                    String name = dep.get("name").getAsString();
                    String version = dep.get("version").getAsString();
                    dependencies.add(new String[]{group, name, version});
                    logger.debug("Found transitive dependency: " + group + ":" + name + ":" + version);
                }
            }
        } catch (IOException e) {
            logger.debug("Could not read github-dependencies.json from " + jar.getName() + ": " + e.getMessage());
        }
        return dependencies;
    }

    /**
     * Downloads a release asset JAR and recursively resolves its transitive GitHub dependencies.
     * Each dependency's JAR is inspected for embedded {@code META-INF/github-dependencies.json}
     * metadata, and any listed dependencies are downloaded recursively. A resolved-set prevents
     * cycles and duplicate downloads.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param version the release version tag
     * @param resolved set of already-resolved dependency keys ({@code "owner:name:version"}) for cycle detection
     * @param collected list that all resolved JAR files (including transitives) are added to
     */
    public void getAssetWithTransitives(String repoOwner, String repoName, String version,
                                        Set<String> resolved, List<File> collected) {
        String key = repoOwner + ":" + repoName + ":" + version;
        if (!resolved.add(key)) {
            logger.debug("Already resolved " + key + ", skipping (cycle prevention).");
            return;
        }

        File jar = getAsset(repoOwner, repoName, version);
        collected.add(jar);

        List<String[]> transitiveDeps = readGithubDependencies(jar);
        for (String[] dep : transitiveDeps) {
            logger.debug("Resolving transitive dependency: " + dep[0] + ":" + dep[1] + ":" + dep[2]);
            getAssetWithTransitives(dep[0], dep[1], dep[2], resolved, collected);
        }
    }

    /**
     * Downloads and caches a classifier-specific JAR asset from a GitHub release.
     *
     * <p>The expected asset name on the release is {@code repoName-classifier.jar}.
     * The file is cached under the same owner directory as {@link #getAsset}.
     *
     * @param repoOwner  the repository owner
     * @param repoName   the repository name
     * @param version    the release version tag
     * @param classifier the artifact classifier (e.g. {@code "api"}, {@code "fat"})
     * @return the downloaded JAR file, or null if no matching asset exists in the release
     */
    public File getAssetWithClassifier(String repoOwner, String repoName, String version, String classifier) {
        logger.debug("Fetching classifier asset '" + classifier + "' for " + repoOwner + "/" + repoName + " " + version);
        File direction = new File(GradleUtils.getGradleHome().resolve("github").toFile(), repoOwner);
        if (!direction.exists() && !direction.mkdirs()) {
            throw new RuntimeException("Failed to create directory: " + direction.getAbsolutePath());
        }
        String assetFileName = repoName + "-" + classifier + "-" + version + ".jar";
        File jar = new File(direction, assetFileName);
        if (jar.exists()) {
            logger.debug("Classifier asset already cached: " + jar.getName());
            return jar;
        }
        JsonObject release = fetchReleaseByTag(repoOwner, repoName, version);
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null || assets.isEmpty()) {
            return null;
        }
        String expectedName = repoName + "-" + classifier + ".jar";
        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.equals(expectedName)) {
                String downloadUrl = asset.get("browser_download_url").getAsString();
                try {
                    downloadAssetFromUrl(jar, downloadUrl, repoOwner, repoName);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to download classifier asset " + expectedName + ": " + e.getMessage(), e);
                }
                return jar;
            }
        }
        logger.debug("No asset named '" + expectedName + "' found in release " + version);
        return null;
    }

    /**
     * Downloads every module asset from a multi-module release (all assets named
     * {@code repoName-<classifier>.jar}, excluding {@code -sources.jar}/{@code -javadoc.jar}). Backs the
     * reserved {@code :all} classifier so a consumer can pull the whole library without listing each module.
     * Each jar is cached under the same owner directory as {@link #getAsset}.
     *
     * @param repoOwner the repository owner
     * @param repoName  the repository name
     * @param version   the release version tag
     * @param collected list that the downloaded module JAR files are added to
     */
    public void getAllModuleAssets(String repoOwner, String repoName, String version, List<File> collected) {
        logger.debug("Fetching all module assets for " + repoOwner + "/" + repoName + " " + version);
        JsonObject release = fetchReleaseByTag(repoOwner, repoName, version);
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null || assets.isEmpty()) {
            throw new RuntimeException("No assets found for release " + version + " of " + repoOwner + "/" + repoName + ".");
        }
        File direction = new File(GradleUtils.getGradleHome().resolve("github").toFile(), repoOwner);
        if (!direction.exists() && !direction.mkdirs()) {
            throw new RuntimeException("Failed to create directory: " + direction.getAbsolutePath());
        }
        String prefix = repoName + "-";
        int count = 0;
        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (!name.startsWith(prefix) || !name.endsWith(".jar")
                    || name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar")) {
                continue;
            }
            String classifier = name.substring(prefix.length(), name.length() - ".jar".length());
            File jar = new File(direction, repoName + "-" + classifier + "-" + version + ".jar");
            if (!jar.exists()) {
                String downloadUrl = asset.get("browser_download_url").getAsString();
                try {
                    downloadAssetFromUrl(jar, downloadUrl, repoOwner, repoName);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to download module asset " + name + ": " + e.getMessage(), e);
                }
            } else {
                logger.debug("Module asset already cached: " + jar.getName());
            }
            collected.add(jar);
            count++;
        }
        if (count == 0) {
            throw new RuntimeException("No module assets ('" + prefix + "*.jar') found in release " + version
                    + " of " + repoOwner + "/" + repoName + ". Was it published with an artifact { modules = true } entry?");
        }
        logger.debug("Resolved " + count + " module asset(s) for " + repoOwner + "/" + repoName + ".");
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

                if (!response.isSuccessful()) {
                    String context = String.format("latest release for %s/%s", repoOwner, repoName);
                    throw new RuntimeException(buildGitHubApiErrorMessage(response, context));
                }
                if (response.body() == null) {
                    throw new RuntimeException("GitHub API returned empty body for latest release " + repoOwner + "/" + repoName + ".");
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

    /**
     * Makes an authenticated POST request to the GitHub API with a JSON body.
     *
     * @param url      the API URL
     * @param jsonBody the JSON request body
     * @return the HTTP response (caller must close)
     * @throws IOException if the request fails
     */
    private Response makeGitHubApiPostRequest(String url, String jsonBody) throws IOException {
        okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, JSON);
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28");

        String apiKey = getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        return httpClient.newCall(requestBuilder.build()).execute();
    }

    /**
     * Reads the git remote "origin" URL from the project directory and parses it
     * into {@code [owner, repo]}.  Supports both HTTPS and SSH remote URLs.
     *
     * @param projectDir the root directory of the Git repository
     * @return a two-element array {@code {owner, repo}}
     * @throws RuntimeException if no "origin" remote is configured or the URL cannot be parsed
     */
    public String[] getRemoteOwnerAndRepo(File projectDir) {
        logger.debug("Resolving GitHub owner/repo from git remote in: " + projectDir.getAbsolutePath());
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (org.eclipse.jgit.lib.Repository repository = builder
                .setGitDir(new File(projectDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()) {
            String remoteUrl = repository.getConfig().getString("remote", "origin", "url");
            if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
                throw new RuntimeException(
                        "No git remote 'origin' found in " + projectDir.getAbsolutePath() + ". "
                        + "Add a remote with: git remote add origin https://github.com/OWNER/REPO");
            }
            logger.debug("Remote origin URL: " + remoteUrl);

            String ownerAndRepo;
            if (remoteUrl.startsWith("git@")) {
                // git@github.com:owner/repo.git
                ownerAndRepo = remoteUrl.split(":")[1];
            } else {
                // https://github.com/owner/repo.git
                String[] parts = remoteUrl.split("/");
                ownerAndRepo = parts[parts.length - 2] + "/" + parts[parts.length - 1];
            }
            if (ownerAndRepo.endsWith(".git")) {
                ownerAndRepo = ownerAndRepo.substring(0, ownerAndRepo.length() - 4);
            }
            String[] result = ownerAndRepo.split("/");
            if (result.length < 2) {
                throw new RuntimeException("Cannot parse owner/repo from remote URL: " + remoteUrl);
            }
            logger.debug("Resolved owner=" + result[0] + " repo=" + result[1]);
            return new String[]{result[0], result[1]};
        } catch (IOException e) {
            throw new RuntimeException("Failed to read git config from " + projectDir + ": " + e.getMessage(), e);
        }
    }

    /**
     * Creates a GitHub release for the given tag, reusing the existing release if the tag already exists.
     *
     * <p>If a release for {@code tagName} already exists it is returned as-is so that
     * additional assets can still be uploaded to it without failing the build.
     *
     * @param owner       the repository owner
     * @param repo        the repository name
     * @param tagName     the git tag for the release (GitHub auto-creates a lightweight tag if absent)
     * @param releaseName the human-readable release title; if null, defaults to {@code tagName}
     * @return the release JSON object (contains {@code upload_url})
     * @throws RuntimeException if auth fails or the API errors
     */
    public JsonObject createRelease(String owner, String repo, String tagName, String releaseName) {
        String title = releaseName != null ? releaseName : tagName;
        logger.debug("Checking for existing release '" + tagName + "' on " + owner + "/" + repo);
        try {
            JsonObject existing = fetchReleaseByTag(owner, repo, tagName);
            logger.log("Release '" + tagName + "' already exists \u2014 uploading assets to existing release.");
            return existing;
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.startsWith("No release found")) {
                throw e;
            }
        }
        logger.debug("Creating release '" + tagName + "' (title: '" + title + "') on " + owner + "/" + repo);
        JsonObject body = new JsonObject();
        body.addProperty("tag_name", tagName);
        body.addProperty("name", title);
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases";
        try (Response response = makeGitHubApiPostRequest(apiUrl, gson.toJson(body))) {
            if (!response.isSuccessful()) {
                String context = "create release " + owner + "/" + repo + " tag " + tagName;
                throw new RuntimeException(buildGitHubApiErrorMessage(response, context));
            }
            if (response.body() == null) {
                throw new RuntimeException("GitHub API returned empty body when creating release " + tagName + ".");
            }
            JsonObject release = gson.fromJson(response.body().string(), JsonObject.class);
            logger.log("Created release '" + tagName + "' on " + owner + "/" + repo);
            return release;
        } catch (IOException e) {
            throw new RuntimeException("IOException creating release: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads a file as a release asset to GitHub.
     *
     * @param uploadUrl the {@code upload_url} from the release object (URI template stripped automatically)
     * @param file      the file to upload
     * @param assetName the asset name as it will appear in the release
     * @throws IOException if the upload fails
     */
    public void uploadReleaseAsset(String uploadUrl, File file, String assetName) throws IOException {
        String cleanUrl = uploadUrl.replace("{?name,label}", "") + "?name=" + assetName;
        logger.debug("Uploading " + file.getName() + " to: " + cleanUrl);

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        okhttp3.MediaType OCTET = okhttp3.MediaType.parse("application/octet-stream");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(fileBytes, OCTET);
        Request.Builder builder = new Request.Builder()
                .url(cleanUrl)
                .post(body)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28");

        String apiKey = getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(buildHttpErrorMessage(response.code(), response.message(),
                        "upload asset " + assetName));
            }
            logger.log("Uploaded " + assetName + " (" + fileBytes.length + " bytes)");
        }
    }
}
