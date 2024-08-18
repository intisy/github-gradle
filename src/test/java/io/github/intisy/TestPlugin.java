package io.github.intisy;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestPlugin {
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
}
