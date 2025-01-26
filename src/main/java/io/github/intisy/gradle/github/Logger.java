package io.github.intisy.gradle.github;

public class Logger {
    private final GithubExtension extension;
    public Logger(GithubExtension extension) {
        this.extension = extension;
    }
    public void log(String message) {
        System.out.println(message);
    }

    public void debug(String message) {
        if (extension.isDebug()) {
            System.out.println(message);
        }
    }
}
