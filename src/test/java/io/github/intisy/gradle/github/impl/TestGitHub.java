package io.github.intisy.gradle.github.impl;

import org.junit.jupiter.api.Test;

public class TestGitHub {
    @Test
    public void testGithub() {
        GitHub.getAsset("SimpleLogger", "Blizzity", "1.12.7");
    }
}
