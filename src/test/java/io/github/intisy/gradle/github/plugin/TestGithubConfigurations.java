package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.Commons;
import org.gradle.api.Project;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestGithubConfigurations {

    @Test
    public void everyDeclaredGithubConfigurationIsRegistered() {
        Project project = Commons.applyPlugin();
        for (String cfg : GithubConfigurations.GITHUB_CONFIGS) {
            assertNotNull(project.getConfigurations().findByName(cfg),
                    cfg + " configuration should be registered");
        }
    }
}
