package io.github.intisy.gradle.github;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.intisy.gradle.github.extension.ArtifactEntry;
import io.github.intisy.gradle.github.extension.GithubExtension;
import io.github.intisy.gradle.github.extension.PublishExtension;

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
        boolean dependsOnBuild = publishTask.getDependsOn().stream()
                .anyMatch(dep -> {
                    if (dep instanceof String) return dep.equals("build");
                    if (dep instanceof Task) return ((Task) dep).getName().equals("build");
                    return dep.toString().contains("build");
                });
        assertTrue(dependsOnBuild, "publishGithub should depend on build");
    }

    // -------------------------------------------------------------------------
    // Extra configurations
    // -------------------------------------------------------------------------

    @Test
    public void testAllGithubConfigurationsRegistered() {
        Project project = Commons.applyPlugin();
        for (String cfg : new String[]{"githubImplementation", "githubApi", "githubCompileOnly",
                "githubCompileOnlyApi", "githubRuntimeOnly"}) {
            assertNotNull(project.getConfigurations().findByName(cfg),
                    cfg + " configuration should be registered");
        }
    }

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
}
