package io.github.intisy.gradle.github.impl.github;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.intisy.gradle.github.api.config.ResourceSettings;
import io.github.intisy.gradle.github.api.log.GitHubLogger;
import io.github.intisy.gradle.github.api.model.Release;
import io.github.intisy.gradle.github.api.model.ReleaseAsset;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An upload over a name the release already carries has to remove the old asset first, because
 * GitHub answers 422 rather than replacing it, and {@code ensureRelease} hands back an existing
 * release instead of failing. Driven against a real loopback {@link HttpServer} so the requests
 * themselves are what is asserted, in the order they were made.
 */
public class TestAssetReplacement {
    private static final String ASSET_NAME = "my-repo.jar";

    @Test
    public void anUploadOverAnExistingAssetDeletesItFirst(@TempDir File directory) throws IOException {
        RecordingServer server = RecordingServer.start(204, 201);
        try {
            GitHub gitHub = makeGitHub(server);

            gitHub.uploadAsset(releaseCarrying(server, ASSET_NAME), jarFile(directory), ASSET_NAME);

            assertEquals(2, server.requests.size(), "expected a delete and then an upload");
            assertEquals("DELETE /assets/1", server.requests.get(0));
            assertTrue(server.requests.get(1).startsWith("POST /uploads"),
                    "the upload must follow the delete, got " + server.requests.get(1));
        } finally {
            server.stop();
        }
    }

    @Test
    public void anUploadOfANewNameDeletesNothing(@TempDir File directory) throws IOException {
        RecordingServer server = RecordingServer.start(201);
        try {
            GitHub gitHub = makeGitHub(server);

            gitHub.uploadAsset(releaseCarrying(server, "some-other-name.jar"), jarFile(directory), ASSET_NAME);

            assertEquals(1, server.requests.size(), "nothing should have been deleted");
            assertTrue(server.requests.get(0).startsWith("POST /uploads"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void anUploadOntoAReleaseWithNoAssetsDeletesNothing(@TempDir File directory) throws IOException {
        RecordingServer server = RecordingServer.start(201);
        try {
            GitHub gitHub = makeGitHub(server);
            Release release = new Release("1", "1.0.0", "1.0.0", "https://example.com/r",
                    server.url("/uploads") + "{?name,label}", Collections.<ReleaseAsset>emptyList());

            gitHub.uploadAsset(release, jarFile(directory), ASSET_NAME);

            assertEquals(1, server.requests.size());
            assertTrue(server.requests.get(0).startsWith("POST /uploads"));
        } finally {
            server.stop();
        }
    }

    /**
     * A refused delete must not fall through into the upload: that reaches GitHub's 422 by a longer
     * route and reports it as an upload problem rather than as the permissions problem it is.
     */
    @Test
    public void aRefusedDeleteFailsWithoutUploading(@TempDir File directory) throws IOException {
        RecordingServer server = RecordingServer.start(403);
        try {
            GitHub gitHub = makeGitHub(server);
            Release release = releaseCarrying(server, ASSET_NAME);
            File jar = jarFile(directory);

            IOException failure = assertThrows(IOException.class,
                    () -> gitHub.uploadAsset(release, jar, ASSET_NAME));

            assertTrue(failure.getMessage().contains(ASSET_NAME), "the failure should name the asset");
            assertEquals(1, server.requests.size(), "the upload must not have been attempted");
            assertEquals("DELETE /assets/1", server.requests.get(0));
        } finally {
            server.stop();
        }
    }

    /**
     * The asset list was read when the release was looked up, so an asset someone else removed in
     * between leaves the call at the state it was trying to reach.
     */
    @Test
    public void anAlreadyDeletedAssetIsNotAFailure(@TempDir File directory) throws IOException {
        RecordingServer server = RecordingServer.start(404, 201);
        try {
            GitHub gitHub = makeGitHub(server);

            gitHub.uploadAsset(releaseCarrying(server, ASSET_NAME), jarFile(directory), ASSET_NAME);

            assertEquals(2, server.requests.size());
            assertTrue(server.requests.get(1).startsWith("POST /uploads"));
        } finally {
            server.stop();
        }
    }

    private Release releaseCarrying(RecordingServer server, String assetName) {
        List<ReleaseAsset> assets = new ArrayList<ReleaseAsset>();
        assets.add(new ReleaseAsset(assetName, server.url("/assets/1")));
        return new Release("1", "1.0.0", "1.0.0", "https://example.com/r",
                server.url("/uploads") + "{?name,label}", assets);
    }

    private File jarFile(File directory) throws IOException {
        File jar = new File(directory, "my-repo.jar");
        Files.write(jar.toPath(), "not really a jar".getBytes(StandardCharsets.UTF_8));
        return jar;
    }

    private GitHub makeGitHub(RecordingServer server) {
        return new GitHub(new SilentLogger(), new ResourceSettings(), new GithubExtension(),
                name -> null, new AbsentCli(), loopbackClient());
    }

    /**
     * @implNote {@link Proxy#NO_PROXY} and a short {@code callTimeout} keep a test driving a
     * loopback-only server from inheriting an ambient proxy or hanging on OkHttp's default
     * unbounded read timeout.
     */
    private static OkHttpClient loopbackClient() {
        return new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .callTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /** Records the method and path of every request, answering the given codes in order. */
    private static final class RecordingServer {
        final List<String> requests = Collections.synchronizedList(new ArrayList<String>());
        private final HttpServer server;

        private RecordingServer(HttpServer server) {
            this.server = server;
        }

        static RecordingServer start(int... responseCodes) throws IOException {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            RecordingServer recording = new RecordingServer(httpServer);
            httpServer.createContext("/", exchange -> {
                int index = recording.requests.size();
                recording.requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                int code = index < responseCodes.length ? responseCodes[index] : 500;
                respond(exchange, code);
            });
            httpServer.start();
            return recording;
        }

        private static void respond(HttpExchange exchange, int code) throws IOException {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }

        String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        void stop() {
            server.stop(0);
        }
    }

    /** Never present, so no test in here can reach the real {@code gh}. */
    private static final class AbsentCli extends GitHubCli {
        AbsentCli() {
            super(new SilentLogger());
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public String authToken() {
            return null;
        }
    }

    private static final class SilentLogger implements GitHubLogger {
        @Override
        public void log(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void warn(String message) {
        }
    }
}
