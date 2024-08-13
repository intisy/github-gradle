package com.github.WildePizza;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;

class MyPlugin implements Plugin<Project> {
    public void apply(Project project) {
		project.getExtensions().add("myplugin", new MyPluginExtension());

		// The quick-n-dirty way
		project.getTasks().create("dirty", task -> task.doLast(action -> System.out.println("(•_•)")));

		// The "right" way
		project.getTasks().create("right", task -> {
			task.setGroup("MyPlugin");
			task.setDescription("Create myfile.txt in the build directory");
			task.doLast(action -> System.out.println("(•_•)"));
		});

		// The "right" way with configuration
		project.getTasks().create("config", MyTask.class, task -> {
			task.setGroup("MyPlugin");
			task.setDescription("Create myfile.txt in the build directory");
			task.setOutputFile(new File(project.getBuildDir(), "otherfile.txt"));
		});

		project.afterEvaluate(proj -> {
			String fileContent = (String) project.getExtensions().getByName("myplugin.fileContent");
			System.out.println(fileContent);
		});
    }
}
