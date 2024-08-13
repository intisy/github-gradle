package com.github.WildePizza;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestRealBuild {

    private final File projectDir = new File(System.getProperty("user.dir") + "/testProjects/simpleProject");
    private final File configuredProjectDir = new File(System.getProperty("user.dir") + "/testProjects/configuredProject");
    private final List<File> pluginClasspath;

    public TestRealBuild() throws IOException {
        pluginClasspath = Files.readAllLines(new File(getClass().getClassLoader().getResource("plugin-classpath.txt").getFile()).toPath()).stream()
                .map(File::new)
                .toList();
    }

    @BeforeEach
    public void setUp() throws IOException {
        cleanBuildDirs(projectDir);
        cleanBuildDirs(configuredProjectDir);
    }

    @AfterEach
    public void tearDown() throws IOException {
        setUp();
    }

    @Test
    public void testDealWithIt() {
        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments("dealwithit")
                .build();

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":dealwithit").getOutcome());
        assertTrue(result.getOutput().contains("(•_•)"));
    }

    @Test
    public void testMyTask() {
        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments("mytask")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mytask").getOutcome());
        assertTrue(new File(projectDir, "build/myfile.txt").exists());
    }

    @Test
    public void testMyOtherTask() {
        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments("myothertask")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":myothertask").getOutcome());
        assertTrue(new File(projectDir, "build/otherfile.txt").exists());
    }

    @Test
    public void testConfiguration() throws IOException {
        File testFile = new File(configuredProjectDir, "build/myfile.txt");

        BuildResult result = GradleRunner.create()
                .withProjectDir(configuredProjectDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments("mytask")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mytask").getOutcome());
        assertTrue(testFile.exists());
        assertEquals("CONFIGURED", Files.readString(testFile.toPath()));
    }

    private void cleanBuildDirs(File projectDir) throws IOException {
        File buildDir = new File(projectDir, "build");
        if (buildDir.exists()) {
            deleteDirectory(buildDir);
        }
    }

    private void deleteDirectory(File dir) throws IOException {
        Files.walk(dir.toPath())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
