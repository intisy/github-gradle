# Github Gradle

Archives containing JAR files are available as [releases](https://github.com/intisy/github-gradle/releases).

## What is Online Gradle?

Online Gradle implements a way to get dependencies from a GitHub asset, so you don't need services like jitpack anymore

## Usage

Using the plugins DSL:

```groovy
plugins {
    id "io.github.intisy.github-gradle" version "1.3.7"
}
```

Using legacy plugin application:

```groovy
buildscript {
    repositories {
        maven {
            url "https://plugins.gradle.org/m2/"
        }
    }
    dependencies {
        classpath "io.github.intisy.github-gradle:1.3.7"
    }
}

apply plugin: "io.github.intisy.github-gradle"
```

Once you have the plugin installed you can use it like so:

```groovy
dependencies {
    githubImplementation "USERNAME:REPOSITORY:TAG"
}
```

## License

[![Apache License 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
