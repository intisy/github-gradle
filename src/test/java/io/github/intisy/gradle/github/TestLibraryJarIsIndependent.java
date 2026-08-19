package io.github.intisy.gradle.github;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the {@code libraryJar} task in {@code build.gradle} produces a jar that is genuinely
 * loadable outside Gradle, not merely one whose source doesn't import {@code org.gradle}. Every
 * check reads the compiled {@code .class} bytes of every jar entry, because a class that only
 * names a type in a field or method signature still records that type in its constant pool, a
 * fact a source-level import grep cannot see.
 *
 * <p>Three independent claims, each with its own mutation test:
 * <ol>
 *     <li>no class in the jar references {@code org.gradle} (the jar needs no Gradle jar on the
 *     runtime classpath at all);</li>
 *     <li>no {@code api}/{@code extension}/{@code utils} class in the jar references
 *     {@code com.google.gson} (gson stays an internal detail of {@code impl.github.GitHub}, which talks
 *     to the GitHub REST API in JSON and is declared as a runtime dependency of the published
 *     jar instead, exactly like {@code org.eclipse.jgit}, which this test does not scan for at
 *     all for the same reason);</li>
 *     <li>every constant-pool reference from one jar class to another
 *     {@code io.github.intisy.gradle.github} class resolves to a class that is itself in the
 *     jar, so the jar cannot throw {@code NoClassDefFoundError} against itself.</li>
 * </ol>
 *
 * @implNote Claim 2 is scoped to non-{@code impl} classes rather than the whole jar. {@code
 * impl.github.GitHub} itself unavoidably references {@code com.google.gson} throughout its own
 * GitHub-REST-API plumbing (a {@code Gson} field, several {@code JsonObject}/{@code JsonArray}
 * internals); {@code TestCapabilityCoverage} already guarantees none of that leaks onto the four
 * capability interfaces' own signatures. Scanning impl's internals for gson too would make this
 * assertion permanently false against correct code, so it is not part of what this test protects;
 * what it protects is that gson never becomes visible on the {@code api}/{@code extension}/{@code
 * utils} surface a consumer actually compiles against.
 */
public class TestLibraryJarIsIndependent {

    private static final String IMPL_PACKAGE_PREFIX = "io/github/intisy/gradle/github/impl/";
    private static final String OWN_PACKAGE_PREFIX = "io/github/intisy/gradle/github/";
    private static final byte[] GRADLE_MARKER = asciiBytes("org/gradle");
    private static final byte[] GSON_MARKER = asciiBytes("com/google/gson");

    @Test
    public void libraryJarNeverReferencesGradle() throws IOException {
        Map<String, byte[]> classes = readClassEntries(findLibraryJar());
        assertTrue(classes.size() > 0, "The library jar contains no .class entries.");
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            assertFalse(containsSubsequence(entry.getValue(), GRADLE_MARKER),
                    entry.getKey() + " references org/gradle; the library jar must be usable without Gradle on the classpath.");
        }
    }

    @Test
    public void nonImplLibraryJarClassesNeverReferenceGson() throws IOException {
        Map<String, byte[]> classes = readClassEntries(findLibraryJar());
        assertTrue(classes.size() > 0, "The library jar contains no .class entries.");
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            if (entry.getKey().startsWith(IMPL_PACKAGE_PREFIX)) {
                continue;
            }
            assertFalse(containsSubsequence(entry.getValue(), GSON_MARKER),
                    entry.getKey() + " references com/google/gson; gson must stay confined to impl.");
        }
    }

    @Test
    public void libraryJarIsSelfContained() throws IOException {
        Map<String, byte[]> classes = readClassEntries(findLibraryJar());
        assertTrue(classes.size() > 0, "The library jar contains no .class entries.");
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            String className = entry.getKey();
            for (String referenced : findOwnPackageReferences(entry.getValue())) {
                if (referenced.equals(className)) {
                    continue;
                }
                assertTrue(classes.containsKey(referenced),
                        className + " references " + referenced + ", which is not an entry in the library jar.");
            }
        }
    }

    private static File findLibraryJar() {
        File libsDir = new File("build/libs");
        File[] candidates = libsDir.listFiles((dir, name) -> name.startsWith("github-gradle-api-") && name.endsWith(".jar"));
        if (candidates == null || candidates.length == 0) {
            throw new IllegalStateException("No library jar (github-gradle-api-*.jar) found under "
                    + libsDir.getAbsolutePath() + ". Run './gradlew libraryJar' first.");
        }
        if (candidates.length > 1) {
            throw new IllegalStateException("Multiple library jar candidates found under " + libsDir.getAbsolutePath()
                    + ": " + java.util.Arrays.toString(candidates));
        }
        return candidates[0];
    }

    private static Map<String, byte[]> readClassEntries(File jarFile) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                String className = entry.getName().substring(0, entry.getName().length() - ".class".length());
                classes.put(className, readAllBytes(jar, entry));
            }
        }
        return classes;
    }

    private static byte[] readAllBytes(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static Set<String> findOwnPackageReferences(byte[] classBytes) {
        Set<String> found = new HashSet<>();
        byte[] prefix = asciiBytes(OWN_PACKAGE_PREFIX);
        int i = 0;
        while (i <= classBytes.length - prefix.length) {
            if (matchesAt(classBytes, i, prefix)) {
                int start = i;
                int end = i;
                while (end < classBytes.length && isClassNameByte(classBytes[end])) {
                    end++;
                }
                found.add(new String(classBytes, start, end - start, StandardCharsets.US_ASCII));
                i = end;
            } else {
                i++;
            }
        }
        return found;
    }

    private static boolean matchesAt(byte[] haystack, int offset, byte[] needle) {
        for (int j = 0; j < needle.length; j++) {
            if (haystack[offset + j] != needle[j]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isClassNameByte(byte b) {
        char c = (char) (b & 0xFF);
        return Character.isLetterOrDigit(c) || c == '/' || c == '_' || c == '$';
    }

    private static boolean containsSubsequence(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] asciiBytes(String s) {
        byte[] bytes = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            bytes[i] = (byte) s.charAt(i);
        }
        return bytes;
    }
}
