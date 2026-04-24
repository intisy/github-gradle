# Consumer Example (Groovy DSL)

Demonstrates pulling a JAR dependency from a GitHub release using the `githubImplementation` configuration added by the `io.github.intisy.github-gradle` plugin.

## What it shows

- Applying the plugin via the `plugins { }` DSL
- Setting a GitHub access token through the `github { }` extension
- Declaring a dependency with `githubImplementation "OWNER:REPO:VERSION"`

## How to run

```bash
# List resolved dependencies (including the GitHub-hosted JAR)
gradle dependencies

# Compile against the GitHub-hosted dependency
gradle build
```

> **Prerequisite:** Replace `ghp_YOUR_TOKEN_HERE` in `build.gradle` with a valid GitHub personal access token that has read access to the target repository.
