package com.github.WildePizza;

import com.github.WildePizza.MyPlugin;
import com.github.WildePizza.MyPluginExtension;
import com.github.WildePizza.MyTask;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class TestMyPlugin {

    @Test
    public void testDealWithIt() {
        Project project = ProjectBuilder.builder().withName("hello-world").build();
        project.getPluginManager().apply(MyPlugin.class);

        assertNotNull(project.getTasks().findByName("dirty"));
        assertNotNull(project.getTasks().findByName("right"));
        assertNotNull(project.getTasks().findByName("config"));
    }

    @Test
    public void testMyTask() {
        Project project = ProjectBuilder.builder().withName("hello-world").build();
        project.getPluginManager().apply(MyPlugin.class);

        assertTrue(project.getTasks().findByName("mytask") instanceof MyTask);
        assertEquals(new File(project.getBuildDir(), "myfile.txt"), ((MyTask) project.getTasks().findByName("mytask")).getOutputFile());
    }

    @Test
    public void testMyOtherTask() {
        Project project = ProjectBuilder.builder().withName("hello-world").build();
        project.getPluginManager().apply(MyPlugin.class);

        assertTrue(project.getTasks().findByName("myothertask") instanceof MyTask);
        assertEquals(new File(project.getBuildDir(), "otherfile.txt"), ((MyTask) project.getTasks().findByName("myothertask")).getOutputFile());
    }

    @Test
    public void testHasExtension() {
        Project project = ProjectBuilder.builder().withName("hello-world").build();
        project.getPluginManager().apply(MyPlugin.class);

        MyPluginExtension extension = (MyPluginExtension) project.getExtensions().getByName("myplugin");
        assertNotNull(extension);
        assertEquals("¯\\_(ツ)_/¯", extension.getFileContent());
    }
}
