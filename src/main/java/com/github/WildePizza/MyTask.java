package com.github.WildePizza;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class MyTask extends DefaultTask {
    private File outputFile;

    public MyTask() {
        this.outputFile = new File(getProject().getBuildDir(), "myfile.txt");
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @TaskAction
    public void action() throws IOException {
        // Ensure the parent directories exist
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        // Create the file if it does not exist
        if (!outputFile.exists()) {
            outputFile.createNewFile();
        }

        // Set the content of the file
        String fileContent = (String) getProject().getExtensions().getByName("myplugin.fileContent");
        Files.write(outputFile.toPath(), fileContent.getBytes());
    }
}
