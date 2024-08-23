package io.github.intisy;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;

public class Commons {
    public static Project applyPlugin() {
        Project project = ProjectBuilder.builder().withName("hello-world").build();
        project.getPluginManager().apply(Main.class);
        return project;
    }
}
