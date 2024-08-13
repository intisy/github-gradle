package com.github.WildePizza.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileCreator {
    private File outputFile;

    public FileCreator(File outputFile) {
        this.outputFile = outputFile;
    }

    public void create() throws IOException {
        // Ensure the parent directories exist
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        // Create the file if it does not exist
        if (!outputFile.exists()) {
            outputFile.createNewFile();
        }

        // Set the content of the file
        String content = "HELLO FROM MY PLUGIN";
        Files.write(outputFile.toPath(), content.getBytes());
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }
}
