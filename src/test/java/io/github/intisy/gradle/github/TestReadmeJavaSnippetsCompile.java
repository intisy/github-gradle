package io.github.intisy.gradle.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Extracts every fenced {@code ```java} block from {@code README.md} and compiles it against the
 * published library jar ({@code github-gradle-api-*.jar}, built by the {@code libraryJar} task
 * this test's own {@code test} task depends on), so a snippet that no longer compiles fails the
 * build instead of shipping silently.
 *
 * @implNote A README snippet is written to read naturally inline, mixing {@code import}
 * statements with executable statements the way a reader would paste them into a method body, not
 * as a standalone compilation unit. This test separates the {@code import} lines from the rest and
 * wraps the remainder in a synthetic class's method, so the snippet is compiled exactly as
 * written, only given the surrounding shape Java syntax requires.
 */
public class TestReadmeJavaSnippetsCompile {

    private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+[^;]+;\\s*$");

    @Test
    public void everyReadmeJavaSnippetCompilesAgainstTheLibraryJar(@TempDir File tempDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "No system Java compiler available at test time; skipping the README compile gate.");

        List<String> snippets = extractJavaSnippets(readReadme());
        assertTrue(!snippets.isEmpty(), "Expected at least one ```java fenced block in README.md.");

        File libraryJar = findLibraryJar();

        int index = 0;
        for (String snippet : snippets) {
            index++;
            compileSnippet(compiler, tempDir, libraryJar, "ReadmeSnippet" + index, snippet);
        }
    }

    private static String readReadme() throws IOException {
        File readme = new File("README.md");
        assertTrue(readme.isFile(), "README.md not found at " + readme.getAbsolutePath());
        return new String(Files.readAllBytes(readme.toPath()), StandardCharsets.UTF_8);
    }

    private static List<String> extractJavaSnippets(String readme) {
        List<String> snippets = new ArrayList<String>();
        Matcher matcher = JAVA_FENCE.matcher(readme);
        while (matcher.find()) {
            snippets.add(matcher.group(1));
        }
        return snippets;
    }

    /**
     * @implNote Compiled at {@code -source 8 -target 8}, matching this project's own JDK 1.8
     * source-compatibility rule, so a snippet using a post-8 API or syntax fails this gate instead
     * of only failing for a reader on an older JDK than whichever one happens to run the tests.
     */
    private static void compileSnippet(JavaCompiler compiler, File tempDir, File libraryJar, String className, String snippet) throws IOException {
        String source = wrapAsCompilationUnit(snippet, className);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        File outDir = new File(tempDir, className);
        if (!outDir.mkdirs()) {
            throw new IOException("Failed to create " + outDir.getAbsolutePath());
        }

        boolean success;
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(outDir));
            fileManager.setLocation(StandardLocation.CLASS_PATH, Arrays.asList(libraryJar));

            JavaFileObject sourceFile = new StringSource(className, source);
            List<String> options = Arrays.asList("-source", "8", "-target", "8");
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, Arrays.asList(sourceFile));
            success = task.call();
        } finally {
            fileManager.close();
        }

        if (!success) {
            StringBuilder message = new StringBuilder("README.md's ```java snippet #" + className
                    + " failed to compile against the library jar:\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                message.append(diagnostic.toString()).append('\n');
            }
            message.append("---- generated source ----\n").append(source);
            throw new AssertionError(message.toString());
        }
    }

    /**
     * @implNote {@code import} lines are hoisted above the class declaration (Java requires them
     * there); everything else becomes the body of a method declared {@code throws Exception}, since
     * a README snippet may call a method (for example {@code Downloads#download}) that declares a
     * checked {@code IOException}.
     */
    private static String wrapAsCompilationUnit(String snippet, String className) {
        StringBuilder imports = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (String line : snippet.split("\n", -1)) {
            if (IMPORT_LINE.matcher(line.trim()).matches()) {
                imports.append(line).append('\n');
            } else {
                body.append(line).append('\n');
            }
        }
        return imports
                + "public class " + className + " {\n"
                + "    static void run() throws Exception {\n"
                + body
                + "    }\n"
                + "}\n";
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
                    + ": " + Arrays.toString(candidates));
        }
        return candidates[0];
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String code;

        StringSource(String className, String code) {
            super(URI.create("string:///" + className + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
