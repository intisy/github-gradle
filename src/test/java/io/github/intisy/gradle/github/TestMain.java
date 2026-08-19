package io.github.intisy.gradle.github;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.intisy.gradle.github.api.config.AuthSettings;
import io.github.intisy.gradle.github.api.config.CliSettings;
import io.github.intisy.gradle.github.api.config.ResilienceSettings;
import io.github.intisy.gradle.github.plugin.extension.ArtifactEntry;
import io.github.intisy.gradle.github.plugin.extension.GitSourceEntry;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import io.github.intisy.gradle.github.plugin.extension.JarSourceEntry;
import io.github.intisy.gradle.github.plugin.extension.PublishExtension;
import io.github.intisy.gradle.github.plugin.extension.SourcesExtension;

public class TestMain {

    @Test
    public void testGithubImplementation() {
        Project project = Commons.applyPlugin();
        project.getDependencies().add("githubImplementation", "Blizzity:SimpleLogger:1.12.7");
    }

    @Test
    public void testPrintGithubDependenciesTask() {
        Project project = Commons.applyPlugin();
        project.getDependencies().add("githubImplementation", "com.github.intisy:my-library:1.0.0");
        Task task = project.getTasks().findByName("printGithubDependencies");
        assertNotNull(task);
        for (Action<? super Task> a : task.getActions()) {
            System.out.println("Executing task " + task.getName());
            a.execute(task);
        }
    }

    @Test
    public void testPublishGithubTaskExists() {
        Project project = Commons.applyPlugin();
        Task task = project.getTasks().findByName("publishGithub");
        assertNotNull(task, "publishGithub task should be registered by the plugin");
    }

    @Test
    public void testPublishGithubTaskDependsOnBuild() {
        Project project = Commons.applyPlugin();
        Task publishTask = project.getTasks().findByName("publishGithub");
        assertNotNull(publishTask, "publishGithub task should exist");
        Set<? extends Task> resolvedDependencies = publishTask.getTaskDependencies().getDependencies(publishTask);
        boolean dependsOnBuild = false;
        for (Task dependency : resolvedDependencies) {
            if (dependency.getName().equals("build")) {
                dependsOnBuild = true;
                break;
            }
        }
        assertTrue(dependsOnBuild, "publishGithub should depend on build");
    }

    // -------------------------------------------------------------------------
    // Full registration surface
    // -------------------------------------------------------------------------

    @Test
    public void testGithubExtensionIsRegisteredAsGithubExtension() {
        Project project = Commons.applyPlugin();
        Object github = project.getExtensions().findByName("github");
        assertNotNull(github, "github extension should be registered");
        assertTrue(github instanceof GithubExtension, "github extension should be a GithubExtension");
    }

    @Test
    public void testAllPluginTasksAreRegistered() {
        Project project = Commons.applyPlugin();
        for (String taskName : new String[]{"processGitHubResources", "generateGithubDependencyMetadata",
                "printGithubDependencies", "updateGithubDependencies", "publishGithub"}) {
            assertNotNull(project.getTasks().findByName(taskName), taskName + " task should be registered");
        }
    }

    @Test
    public void testProcessResourcesDependsOnGithubTasks() {
        Project project = Commons.applyPlugin();
        Task processResources = project.getTasks().findByName("processResources");
        assertNotNull(processResources, "processResources task should exist");
        Set<? extends Task> resolvedDependencies = processResources.getTaskDependencies().getDependencies(processResources);
        Set<String> dependencyNames = new HashSet<>();
        for (Task dependency : resolvedDependencies) {
            dependencyNames.add(dependency.getName());
        }
        assertTrue(dependencyNames.contains("processGitHubResources"),
                "processResources should depend on processGitHubResources");
        assertTrue(dependencyNames.contains("generateGithubDependencyMetadata"),
                "processResources should depend on generateGithubDependencyMetadata");
    }

    // -------------------------------------------------------------------------
    // Extra configurations
    // -------------------------------------------------------------------------

    @Test
    public void testGithubApiConfigAcceptsDependency() {
        Project project = Commons.applyPlugin();
        project.getDependencies().add("githubApi", "my-org:my-lib:2.0.0");
        assertEquals(1, project.getConfigurations().getByName("githubApi").getDependencies().size());
    }

    @Test
    public void testGithubCompileOnlyConfigAcceptsDependency() {
        Project project = Commons.applyPlugin();
        project.getDependencies().add("githubCompileOnly", "my-org:annotations:1.0.0");
        assertEquals(1, project.getConfigurations().getByName("githubCompileOnly").getDependencies().size());
    }

    @Test
    public void testGithubRuntimeOnlyConfigAcceptsDependency() {
        Project project = Commons.applyPlugin();
        project.getDependencies().add("githubRuntimeOnly", "my-org:driver:3.5.0");
        assertEquals(1, project.getConfigurations().getByName("githubRuntimeOnly").getDependencies().size());
    }

    // -------------------------------------------------------------------------
    // PublishExtension — basic fields
    // -------------------------------------------------------------------------

    private PublishExtension getPublishExt(Project project) {
        return project.getExtensions().getByType(PublishExtension.class);
    }

    @Test
    public void testPublishExtensionDefaultsAreNull() {
        Project project = Commons.applyPlugin();
        PublishExtension ext = getPublishExt(project);
        assertNull(ext.getOwner(),   "owner should default to null");
        assertNull(ext.getRepo(),    "repo should default to null");
        assertNull(ext.getVersion(), "version should default to null");
        assertNull(ext.getJar(),     "jar should default to null");
    }

    @Test
    public void testPublishExtensionOwnerAndRepo() {
        Project project = Commons.applyPlugin();
        PublishExtension ext = getPublishExt(project);
        ext.setOwner("my-org");
        ext.setRepo("my-repo");
        assertEquals("my-org",  ext.getOwner());
        assertEquals("my-repo", ext.getRepo());
    }

    @Test
    public void testPublishExtensionVersion() {
        Project project = Commons.applyPlugin();
        PublishExtension ext = getPublishExt(project);
        ext.setVersion("3.1.4");
        assertEquals("3.1.4", ext.getVersion());
    }

    @Test
    public void testPublishExtensionJar() {
        Project project = Commons.applyPlugin();
        PublishExtension ext = getPublishExt(project);
        File jar = new File("build/libs/my-custom.jar");
        ext.setJar(jar);
        assertEquals(jar, ext.getJar());
    }

    @Test
    public void testPublishExtensionAccessibleViaGithubExtension() {
        Project project = Commons.applyPlugin();
        PublishExtension viaTopLevel = project.getExtensions().getByType(PublishExtension.class);
        PublishExtension viaGithub = project.getExtensions()
                .getByType(GithubExtension.class)
                .getPublish();
        assertNotNull(viaTopLevel);
        assertNotNull(viaGithub);
        viaGithub.setOwner("shared-owner");
        assertEquals("shared-owner", viaTopLevel.getOwner());
    }

    // -------------------------------------------------------------------------
    // PublishExtension — multi-artifact / classifier
    // -------------------------------------------------------------------------

    @Test
    public void testArtifactsListIsEmptyByDefault() {
        Project project = Commons.applyPlugin();
        PublishExtension ext = getPublishExt(project);
        assertTrue(ext.getArtifacts().isEmpty(), "artifacts should be empty by default");
    }

    @Test
    public void testAddSingleArtifactViaAction() {
        Project project = Commons.applyPlugin();
        PublishExtension ext = getPublishExt(project);
        File jar = new File("build/libs/my-lib.jar");
        ext.artifact(entry -> {
            entry.setJar(jar);
            entry.setClassifier("");
        });
        List<ArtifactEntry> entries = ext.getArtifacts();
        assertEquals(1, entries.size());
        assertEquals(jar, entries.get(0).getJar());
        assertEquals("", entries.get(0).getClassifier());
    }

    @Test
    public void testAddMultipleArtifacts() {
        Project project = Commons.applyPlugin();
        PublishExtension ext = getPublishExt(project);
        ext.artifact(e -> { e.setClassifier("");    e.setJar(new File("build/libs/my-lib.jar")); });
        ext.artifact(e -> { e.setClassifier("api"); e.setJar(new File("build/libs/my-lib-api.jar")); });
        ext.artifact(e -> { e.setClassifier("fat"); e.setJar(new File("build/libs/my-lib-fat.jar")); });
        assertEquals(3, ext.getArtifacts().size());
        assertEquals("",    ext.getArtifacts().get(0).getClassifier());
        assertEquals("api", ext.getArtifacts().get(1).getClassifier());
        assertEquals("fat", ext.getArtifacts().get(2).getClassifier());
    }

    @Test
    public void testArtifactsBlockViaAction() {
        Project project = Commons.applyPlugin();
        PublishExtension ext = getPublishExt(project);
        ext.artifacts(e -> {
            e.artifact(a -> { a.setClassifier("thin"); a.setJar(new File("thin.jar")); });
            e.artifact(a -> { a.setClassifier("uber"); a.setJar(new File("uber.jar")); });
        });
        assertEquals(2, ext.getArtifacts().size());
        assertEquals("thin", ext.getArtifacts().get(0).getClassifier());
        assertEquals("uber", ext.getArtifacts().get(1).getClassifier());
    }

    @Test
    public void testArtifactEntryNullClassifierNormalisedToEmpty() {
        ArtifactEntry entry = new ArtifactEntry();
        entry.setClassifier(null);
        assertEquals("", entry.getClassifier());
    }

    @Test
    public void testArtifactEntryDefaultClassifierIsEmpty() {
        ArtifactEntry entry = new ArtifactEntry();
        assertEquals("", entry.getClassifier());
    }

    // -------------------------------------------------------------------------
    // CliSettings — nested cli { } block
    // -------------------------------------------------------------------------

    @Test
    public void testCliSettingsDefaults() {
        CliSettings cli = new GithubExtension().getCli();
        assertNotNull(cli, "cli extension should be available");
        assertFalse(cli.isEnabled(), "cli.enabled should default to false");
        assertTrue(cli.isFallback(), "cli.fallback should default to true");
    }

    @Test
    public void testCliBlockViaAction() {
        GithubExtension github = new GithubExtension();
        github.cli(cli -> {
            cli.setEnabled(true);
            cli.setFallback(false);
        });
        assertTrue(github.getCli().isEnabled());
        assertFalse(github.getCli().isFallback());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedUseCliDelegatesToCliEnabled() {
        GithubExtension github = new GithubExtension();
        github.setUseCli(true);
        assertTrue(github.getCli().isEnabled(), "setUseCli should delegate to cli.enabled");
        assertTrue(github.isUseCli(), "isUseCli should reflect cli.enabled");
        github.getCli().setEnabled(false);
        assertFalse(github.isUseCli(), "isUseCli should mirror cli.enabled both ways");
    }

    // -------------------------------------------------------------------------
    // AuthSettings — nested auth { } block
    // -------------------------------------------------------------------------

    @Test
    public void testAuthSettingsDefaultsAreNull() {
        AuthSettings auth = new GithubExtension().getAuth();
        assertNotNull(auth, "auth extension should be available");
        assertNull(auth.getToken(),     "auth.token should default to null");
        assertNull(auth.getTokenFile(), "auth.tokenFile should default to null");
        assertNull(auth.getSshKey(),    "auth.sshKey should default to null");
    }

    @Test
    public void testAuthBlockViaAction() {
        GithubExtension github = new GithubExtension();
        File tokenFile = new File("secrets/gh.txt");
        File sshKey = new File("id_ed25519");
        github.auth(auth -> {
            auth.setToken("ghp_abc");
            auth.setTokenFile(tokenFile);
            auth.setSshKey(sshKey);
        });
        assertEquals("ghp_abc", github.getAuth().getToken());
        assertEquals(tokenFile, github.getAuth().getTokenFile());
        assertEquals(sshKey,    github.getAuth().getSshKey());
    }

    // -------------------------------------------------------------------------
    // ResilienceSettings — nested resilience { } block
    // -------------------------------------------------------------------------

    @Test
    public void testResilienceDefaultAndBlock() {
        GithubExtension github = new GithubExtension();
        ResilienceSettings resilience = github.getResilience();
        assertNotNull(resilience, "resilience extension should be available");
        assertFalse(resilience.isSkipOnRateLimit(), "skipOnRateLimit should default to false");
        github.resilience(r -> r.setSkipOnRateLimit(true));
        assertTrue(github.getResilience().isSkipOnRateLimit());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedSkipOnRateLimitDelegatesToResilience() {
        GithubExtension github = new GithubExtension();
        github.setSkipOnRateLimit(true);
        assertTrue(github.getResilience().isSkipOnRateLimit(), "setSkipOnRateLimit should delegate to resilience");
        assertTrue(github.isSkipOnRateLimit(), "isSkipOnRateLimit should reflect resilience");
    }

    // -------------------------------------------------------------------------
    // SourcesExtension — nested sources { } extension, separate from github { }
    // -------------------------------------------------------------------------

    @Test
    public void testSourcesExtensionIsRegisteredAsSourcesExtension() {
        Project project = Commons.applyPlugin();
        Object sources = project.getExtensions().findByName("sources");
        assertNotNull(sources, "sources extension should be registered");
        assertTrue(sources instanceof SourcesExtension, "sources extension should be a SourcesExtension");
    }

    @Test
    public void testSourcesExtensionIsSeparateFromGithubExtension() {
        Project project = Commons.applyPlugin();
        Object github = project.getExtensions().findByName("github");
        Object sources = project.getExtensions().findByName("sources");
        assertNotNull(github);
        assertNotNull(sources);
        assertFalse(github == sources, "github and sources must be independent extensions");
    }

    @Test
    public void testSourcesGitAndJarListsAreEmptyByDefault() {
        SourcesExtension sources = new SourcesExtension();
        assertTrue(sources.getGitSources().isEmpty());
        assertTrue(sources.getJarSources().isEmpty());
    }

    @Test
    public void testSourcesGitBlockViaAction() {
        SourcesExtension sources = new SourcesExtension();
        sources.git(entry -> {
            entry.setUrl("https://gitlab.com/me/lib.git");
            entry.setRef("main");
        });
        assertEquals(1, sources.getGitSources().size());
        GitSourceEntry entry = sources.getGitSources().get(0);
        assertEquals("https://gitlab.com/me/lib.git", entry.getUrl());
        assertEquals("main", entry.getRef());
        assertEquals("implementation", entry.getInto(), "into should default to implementation");
    }

    @Test
    public void testSourcesGitBlockIsRepeatable() {
        SourcesExtension sources = new SourcesExtension();
        sources.git(e -> e.setUrl("https://gitlab.com/me/one.git"));
        sources.git(e -> e.setUrl("https://gitlab.com/me/two.git"));
        sources.git(e -> e.setUrl("https://gitlab.com/me/three.git"));
        assertEquals(3, sources.getGitSources().size());
        assertEquals("https://gitlab.com/me/one.git", sources.getGitSources().get(0).getUrl());
        assertEquals("https://gitlab.com/me/two.git", sources.getGitSources().get(1).getUrl());
        assertEquals("https://gitlab.com/me/three.git", sources.getGitSources().get(2).getUrl());
    }

    @Test
    public void testSourcesJarBlockViaActionWithHeaderAndSha256() {
        SourcesExtension sources = new SourcesExtension();
        sources.jar(entry -> {
            entry.setUrl("https://nexus.internal/libs/foo-1.0.jar");
            entry.header("Authorization", "Bearer my-token");
            entry.setSha256("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d");
        });
        assertEquals(1, sources.getJarSources().size());
        JarSourceEntry entry = sources.getJarSources().get(0);
        assertEquals("https://nexus.internal/libs/foo-1.0.jar", entry.getUrl());
        assertEquals("Bearer my-token", entry.getHeaders().get("Authorization"));
        assertEquals("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d", entry.getSha256());
        assertEquals("implementation", entry.getInto(), "into should default to implementation");
    }

    @Test
    public void testSourcesJarBlockIsRepeatable() {
        SourcesExtension sources = new SourcesExtension();
        sources.jar(e -> e.setUrl("https://nexus.internal/libs/one.jar"));
        sources.jar(e -> e.setUrl("https://nexus.internal/libs/two.jar"));
        assertEquals(2, sources.getJarSources().size());
    }

    @Test
    public void testSourcesJarIntoIsConfigurable() {
        SourcesExtension sources = new SourcesExtension();
        sources.jar(entry -> {
            entry.setUrl("https://nexus.internal/libs/foo-1.0.jar");
            entry.setInto("api");
        });
        assertEquals("api", sources.getJarSources().get(0).getInto());
    }

    @Test
    public void testJarSourceEntryHeadersDefaultToEmpty() {
        JarSourceEntry entry = new JarSourceEntry();
        assertTrue(entry.getHeaders().isEmpty());
        assertNull(entry.getSha256());
    }

    @Test
    public void testGitSourceEntryRefDefaultsToNull() {
        GitSourceEntry entry = new GitSourceEntry();
        assertNull(entry.getRef());
        assertEquals("implementation", entry.getInto());
    }
}
