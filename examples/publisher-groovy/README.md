# Publisher Example (Groovy DSL)

Demonstrates publishing a JAR as a GitHub release asset using the `publishGithub` task provided by the `io.github.intisy.github-gradle` plugin.

## What it shows

- **Form A** — Top-level `publishGithub { }` extension block
- **Form B** — Nested `github { publish { } }` extension block
- Both forms configure the same underlying `PublishExtension`; use whichever style you prefer

All four publish fields (`owner`, `repo`, `version`, `jar`) are optional. When omitted, the plugin auto-detects values from the git remote and build output.

## How to run

```bash
# Build the JAR and publish it as a GitHub release asset in one step
gradle publishGithub
```

> **Prerequisite:** Replace `ghp_YOUR_TOKEN_HERE` in `build.gradle` with a valid GitHub personal access token that has write access to the target repository's releases.
