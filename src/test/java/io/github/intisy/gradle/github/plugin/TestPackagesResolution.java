package io.github.intisy.gradle.github.plugin;

import io.github.intisy.gradle.github.api.capability.Credentials;
import io.github.intisy.gradle.github.plugin.extension.PackageSourceEntry;
import io.github.intisy.gradle.github.plugin.extension.PackagesExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each {@code github { packages { from } }} entry has to become a real Maven repository carrying
 * the resolved token, because that is the only way a dependency resolves with the pom and module
 * metadata a release asset cannot carry.
 */
public class TestPackagesResolution {

    @Test
    public void anEntryBecomesAMavenRepositoryAtThePackagesUrl() {
        Project project = projectWith(packagesFrom("my-org/libs"), new FixedToken("a-token"));

        MavenArtifactRepository repository = onlyMavenRepository(project);
        assertEquals("https://maven.pkg.github.com/my-org/libs", repository.getUrl().toString());
    }

    @Test
    public void theRepositoryCarriesTheResolvedToken() {
        Project project = projectWith(packagesFrom("my-org/libs"), new FixedToken("a-token"));

        PasswordCredentials credentials = onlyMavenRepository(project).getCredentials(PasswordCredentials.class);
        assertEquals("a-token", credentials.getPassword());
        assertNotNull(credentials.getUsername(), "Gradle requires a username even though GitHub ignores it");
        assertFalse(credentials.getUsername().isEmpty());
    }

    @Test
    public void everyEntryIsRegistered() {
        PackagesExtension extension = new PackagesExtension();
        extension.from("my-org/libs");
        extension.from("my-org/core");
        extension.from("other-org/tools");

        Project project = projectWith(extension, new FixedToken("a-token"));

        assertEquals(3, mavenRepositories(project).size());
    }

    @Test
    public void nothingIsRegisteredWhenNoEntryIsDeclared() {
        Project project = projectWith(new PackagesExtension(), new FixedToken("a-token"));

        assertTrue(mavenRepositories(project).isEmpty());
    }

    /**
     * A narrowed entry is still a fully registered repository. What the filter then does is NOT
     * asserted: reading a content filter back needs
     * {@code org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository}, and a test
     * resting on a Gradle internal breaks on a Gradle upgrade for no gain. The filter is an
     * optimisation, so what is worth defending is that adding it does not cost the registration.
     */
    @Test
    public void aNarrowedEntryIsStillRegisteredInFull() {
        PackagesExtension extension = new PackagesExtension();
        PackageSourceEntry entry = extension.from("my-org/libs");
        entry.setGroup("com.example");

        Project project = projectWith(extension, new FixedToken("a-token"));

        MavenArtifactRepository repository = onlyMavenRepository(project);
        assertEquals("https://maven.pkg.github.com/my-org/libs", repository.getUrl().toString());
        assertEquals("a-token", repository.getCredentials(PasswordCredentials.class).getPassword());
    }

    @Test
    public void aMissingTokenIsWarnedAboutRatherThanIgnored() {
        CapturingLogger logger = new CapturingLogger();

        Project project = ProjectBuilder.builder().build();
        PackagesResolution.apply(project, logger, packagesFrom("my-org/libs"), new FixedToken(null));
        evaluate(project);

        assertEquals(1, logger.warnings.size(), "a token-less packages entry cannot resolve and must say so");
        assertTrue(logger.warnings.get(0).contains("my-org/libs"), "the warning should name the entry");
    }

    @Test
    public void aRepositoryWithoutASlashIsRefusedWhereItIsDeclared() {
        PackagesExtension extension = new PackagesExtension();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> extension.from("libs"));

        assertTrue(failure.getMessage().contains("libs"), "the failure should quote what was declared");
    }

    @Test
    public void aRepositoryWithAnEmptyHalfIsRefused() {
        PackagesExtension extension = new PackagesExtension();

        assertThrows(IllegalArgumentException.class, () -> extension.from("my-org/"));
        assertThrows(IllegalArgumentException.class, () -> extension.from("/libs"));
        assertThrows(IllegalArgumentException.class, () -> extension.from("my-org/libs/extra"));
    }

    private PackagesExtension packagesFrom(String repository) {
        PackagesExtension extension = new PackagesExtension();
        extension.from(repository);
        return extension;
    }

    private Project projectWith(PackagesExtension extension, Credentials credentials) {
        Project project = ProjectBuilder.builder().build();
        PackagesResolution.apply(project, new CapturingLogger(), extension, credentials);
        evaluate(project);
        return project;
    }

    /**
     * @implNote {@code ProjectBuilder} never evaluates, so {@code afterEvaluate} actions have to be
     * fired by hand for anything registered inside one to exist.
     */
    private void evaluate(Project project) {
        ((org.gradle.api.internal.project.ProjectInternal) project).evaluate();
    }

    private MavenArtifactRepository onlyMavenRepository(Project project) {
        List<MavenArtifactRepository> repositories = mavenRepositories(project);
        assertEquals(1, repositories.size(), "expected exactly one repository");
        return repositories.get(0);
    }

    private List<MavenArtifactRepository> mavenRepositories(Project project) {
        List<MavenArtifactRepository> found = new ArrayList<MavenArtifactRepository>();
        for (ArtifactRepository repository : project.getRepositories()) {
            if (repository instanceof MavenArtifactRepository) {
                found.add((MavenArtifactRepository) repository);
            }
        }
        return found;
    }

    private static final class FixedToken implements Credentials {
        private final String token;

        FixedToken(String token) {
            this.token = token;
        }

        @Override
        public String apiKey() {
            return token;
        }

        @Override
        public String sshKey() {
            return null;
        }
    }

    private static final class CapturingLogger extends Logger {
        final List<String> warnings = new ArrayList<String>();

        CapturingLogger() {
            super(new io.github.intisy.gradle.github.plugin.extension.GithubExtension());
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    }
}
