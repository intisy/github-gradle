package io.github.intisy.gradle.github;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the layering rule that only {@code api} may call {@code impl}, and nothing else may.
 *
 * <p>Scans every compiled {@code main} class file for a constant-pool reference to {@code
 * io/github/intisy/gradle/github/impl/}. A hit is permitted only when the referencing class is
 * itself under {@code api/} (the facade calling its own implementation) or {@code impl/} (an impl
 * class referencing a sibling), or when its outer class simple name is in {@link
 * #ALLOWED_OUTSIDE_LAYERING} below.
 *
 * <p>{@link #ALLOWED_OUTSIDE_LAYERING} is a fixed, hard-coded record of violations not yet
 * cleaned up. It may only ever shrink, never grow, and it is never derived from the current tree:
 * a gate that regenerates its own baseline from whatever the tree currently does cannot fail.
 *
 * <p>Only {@code build/classes/java/main} is scanned. A unit test in the {@code impl} test
 * package is {@code impl}'s own test and may name {@code impl} types freely; test output is not
 * part of this scan.
 */
public class TestOnlyTheApiCallsImpl {

    /**
     * Every entry needs a one-line reason. Remove an entry only when the named class itself no
     * longer references {@code impl}; never add one without also fixing the class it would cover.
     */
    private static final Set<String> ALLOWED_OUTSIDE_LAYERING = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "Main" // the Gradle plugin entry point; constructs impl.GitHub and impl.Gradle directly, pending a switch to the api facade.
    )));

    private static final String OWN_ROOT = "io/github/intisy/gradle/github/";
    private static final String IMPL_MARKER = "io/github/intisy/gradle/github/impl/";
    private static final String API_PREFIX = OWN_ROOT + "api/";
    private static final String IMPL_PREFIX = OWN_ROOT + "impl/";

    @Test
    public void onlyApiAndImplReferenceImpl() throws IOException {
        File mainClasses = new File("build/classes/java/main");
        assertTrue(mainClasses.isDirectory(), "Expected compiled classes at " + mainClasses.getAbsolutePath()
                + "; run compileJava first.");
        byte[] marker = asciiBytes(IMPL_MARKER);

        List<File> classFiles = new ArrayList<>();
        collectClassFiles(mainClasses, classFiles);
        assertTrue(classFiles.size() > 0, "No compiled classes found under " + mainClasses.getAbsolutePath() + ".");

        for (File classFile : classFiles) {
            String relativePath = relativize(mainClasses, classFile);
            if (!relativePath.startsWith(OWN_ROOT)) {
                continue;
            }
            if (relativePath.startsWith(API_PREFIX) || relativePath.startsWith(IMPL_PREFIX)) {
                continue;
            }
            byte[] classBytes = Files.readAllBytes(classFile.toPath());
            if (!containsSubsequence(classBytes, marker)) {
                continue;
            }
            String outerSimpleName = outerSimpleName(relativePath);
            assertTrue(ALLOWED_OUTSIDE_LAYERING.contains(outerSimpleName),
                    relativePath + " references impl (" + IMPL_MARKER + ") but is not api, impl, or on the allow-list.");
        }
    }

    private static void collectClassFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectClassFiles(child, out);
            } else if (child.getName().endsWith(".class")) {
                out.add(child);
            }
        }
    }

    private static String relativize(File root, File file) {
        String rootPath = root.getAbsolutePath().replace('\\', '/');
        String filePath = file.getAbsolutePath().replace('\\', '/');
        if (!filePath.startsWith(rootPath)) {
            throw new IllegalStateException(filePath + " is not under " + rootPath);
        }
        String relative = filePath.substring(rootPath.length());
        return relative.startsWith("/") ? relative.substring(1) : relative;
    }

    private static String outerSimpleName(String relativePath) {
        String withoutExtension = relativePath.substring(0, relativePath.length() - ".class".length());
        String fileName = withoutExtension.substring(withoutExtension.lastIndexOf('/') + 1);
        int dollar = fileName.indexOf('$');
        return dollar >= 0 ? fileName.substring(0, dollar) : fileName;
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
