# github-gradle Plugin Examples

Two self-contained Groovy DSL examples demonstrating the `io.github.intisy.github-gradle` plugin.

## Examples

### [consumer-groovy](consumer-groovy/)

Shows how to **consume** a JAR dependency hosted as a GitHub release asset using the `githubImplementation` dependency configuration.

### [publisher-groovy](publisher-groovy/)

Shows how to **publish** a JAR as a GitHub release asset using the `publishGithub` task. Demonstrates both DSL forms:
- Top-level `publishGithub { }` extension
- Nested `github { publish { } }` extension

> **Note:** These are reference examples, not runnable sub-projects of the plugin build. Copy them into a standalone project directory to use them.
