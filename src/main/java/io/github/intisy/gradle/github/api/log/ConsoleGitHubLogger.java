package io.github.intisy.gradle.github.api.log;

/**
 * A {@link GitHubLogger} for use outside a Gradle build, writing every level to {@code System.err}
 * so {@code System.out} stays clean for consumers that parse it.
 */
public class ConsoleGitHubLogger implements GitHubLogger {
    private final boolean debug;

    /**
     * @param debug whether {@link #debug(String)} messages are written.
     */
    public ConsoleGitHubLogger(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void log(String message) {
        System.err.println(message);
    }

    @Override
    public void error(String message) {
        System.err.println(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        System.err.println(message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    @Override
    public void debug(String message) {
        if (debug) {
            System.err.println(message);
        }
    }

    @Override
    public void warn(String message) {
        System.err.println(message);
    }
}
