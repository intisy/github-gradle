package com.github.WildePizza.impl;

import com.github.WildePizza.impl.FileCreator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFileCreator {

    @Test
    public void testCreatesFileWithContent() throws IOException {
        // Create a temporary file
        File tempFile = File.createTempFile("temp", ".tmp");
        tempFile.deleteOnExit(); // Ensures the temp file is deleted after tests

        // Create the FileCreator object and create the file
        FileCreator creator = new FileCreator(tempFile);
        creator.create();

        // Assert that the file exists and contains the expected content
        assertTrue(tempFile.exists());
        assertEquals("HELLO FROM MY PLUGIN", Files.readString(tempFile.toPath()));
    }

    @Test
    public void testCreatesFileIfParentDirMissing() throws IOException {
        // Create a temporary directory and a file inside it
        File tempDir = Files.createTempDirectory("tempDir").toFile();
        File tempFile = new File(tempDir, "testing.tmp");

        // Delete the temp directory to simulate the missing parent directory scenario
        tempDir.delete();

        // Create the FileCreator object and create the file
        FileCreator creator = new FileCreator(tempFile);
        creator.create();

        // Assert that the file exists and contains the expected content
        assertTrue(tempFile.exists());
        assertEquals("HELLO FROM MY PLUGIN", Files.readString(tempFile.toPath()));
    }
}
