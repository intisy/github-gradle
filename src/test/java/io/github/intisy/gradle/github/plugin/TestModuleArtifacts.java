package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.plugin.extension.ArtifactEntry;
import io.github.intisy.gradle.github.plugin.extension.GithubExtension;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code artifact { modules = true }} expands to. The two destinations of {@code publishGithub}
 * have to agree about what a repository publishes, so a root that produces a jar of its own is a
 * module like any other.
 */
public class TestModuleArtifacts {

    @Test
    public void aRootThatProducesAJarIsAModuleToo() {
        Project root = ProjectBuilder.builder().withName("api-host").build();
        root.getPluginManager().apply("java");
        Project kit = ProjectBuilder.builder().withName("kit").withParent(root).build();
        kit.getPluginManager().apply("java");

        List<String> names = assetNames(root, "api-host");

        assertTrue(names.contains("api-host.jar"), "the root's own jar must be published, got " + names);
        assertTrue(names.contains("api-host-kit.jar"), "and so must the module's, got " + names);
        assertEquals(2, names.size(), "exactly one entry per project with a jar, got " + names);
    }

    @Test
    public void aContainerRootContributesNothing() {
        Project root = ProjectBuilder.builder().withName("libs").build();
        Project common = ProjectBuilder.builder().withName("common").withParent(root).build();
        common.getPluginManager().apply("java");
        Project objectstore = ProjectBuilder.builder().withName("objectstore").withParent(root).build();
        objectstore.getPluginManager().apply("java");

        List<String> names = assetNames(root, "libs");

        assertEquals(2, names.size(), "a root with no jar of its own adds nothing, got " + names);
        assertTrue(names.contains("libs-common.jar"));
        assertTrue(names.contains("libs-objectstore.jar"));
    }

    /**
     * The prefix is stripped so a module named after its repository does not double it up.
     */
    @Test
    public void aModuleNamedAfterItsRepositoryKeepsOnePrefix() {
        Project root = ProjectBuilder.builder().withName("dough").build();
        Project module = ProjectBuilder.builder().withName("dough-common").withParent(root).build();
        module.getPluginManager().apply("java");

        assertEquals("dough-common.jar", assetNames(root, "dough").get(0));
    }

    @Test
    public void aBuildWithNoJarAnywhereIsRefused() {
        Project root = ProjectBuilder.builder().withName("empty").build();

        RuntimeException failure = assertThrows(RuntimeException.class, () -> assetNames(root, "empty"));

        assertTrue(failure.getMessage().contains("jar task"), "the failure should say what is missing");
    }

    private List<String> assetNames(Project root, String repo) {
        List<ArtifactEntry> entries = PublishTasks.buildModuleArtifacts(root, repo, new Logger(new GithubExtension()));
        List<String> names = new ArrayList<String>();
        for (ArtifactEntry entry : entries) {
            names.add(PublishTasks.buildAssetName(repo, entry.getClassifier()));
        }
        return names;
    }
}
