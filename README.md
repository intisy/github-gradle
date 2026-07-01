# Github Gradle

Archives containing JAR files are available as [releases](https://github.com/intisy/github-gradle/releases).

## What is github-gradle?

GitHub Gradle implements a way to get dependencies from a GitHub asset, so you don't need services like jitpack anymore

## Usage

Using the plugins DSL:

```groovy
plugins {
    id "io.github.intisy.github-gradle" version "1.3.8"
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
        classpath "io.github.intisy.github-gradle:1.3.8"
    }
}

apply plugin: "io.github.intisy.github-gradle"
```

Once you have the plugin installed you can use it like so:

```groovy
dependencies {
    // OWNER:REPOSITORY:TAG resolves the main JAR from that release
    githubImplementation "intisy:simple-logger:1.12.7"
    // A 4th segment selects a classifier asset (simple-logger-api.jar)
    githubImplementation "intisy:simple-logger:1.12.7:api"
    // The reserved "all" classifier pulls every module of a multi-module release
    githubImplementation "intisy:dough:1.3.0:all"
}
```

## Guide

### Authentication

Public releases resolve without a token, but GitHub caps unauthenticated API use at 60 requests/hour. Provide credentials in the `auth` block to raise that to 5,000/hour and to reach private repositories:

```groovy
github {
    auth {
        token     = "ghp_your_token"        // a Personal Access Token, or
        tokenFile = file("secrets/gh.txt")   // a file that contains one
        sshKey    = file("~/.ssh/id_ed25519") // an SSH private key for git clone/pull
    }
}
```

### Dependency configurations

Every standard Gradle configuration has a github counterpart, all using the OWNER:REPOSITORY:TAG[:CLASSIFIER] coordinate:

```groovy
dependencies {
    githubImplementation "intisy:simple-logger:1.12.7"
    githubApi            "intisy:java-utils:2.0.0"    // leaks to consumers (needs the java-library plugin)
    githubCompileOnly    "intisy:annotations:1.0.0"   // compile classpath only
    githubCompileOnlyApi "intisy:annotations:1.0.0"   // compile only + leaked (needs the java-library plugin)
    githubRuntimeOnly    "intisy:driver:1.0.0"        // runtime classpath only
}
```

### Publishing a release

Configure the publishGithub extension and run `gradle publishGithub` to build the project and upload its JAR(s) as a GitHub release. Every field is optional:

```groovy
publishGithub {
    owner       = "intisy"          // auto-detected from the git remote if omitted
    repo        = "my-repo"         // auto-detected from the git remote if omitted
    version     = "2.0.0"           // defaults to project.version
    tag         = "v2.0.0"          // defaults to version
    releaseName = "Release 2.0.0"   // defaults to tag
    jar         = file("build/libs/my-app.jar") // auto-selected from build/libs if omitted
}
```

### Managing installed dependencies

Run `gradle updateGithubDependencies` to rewrite every github* coordinate in your build files to the latest release tag, or `gradle printGithubDependencies` to list them.

### Resilience options

```groovy
github {
    resilience {
        // On a rate limit, fall back to the cached (outdated) jar or keep the current version instead of failing (default false)
        skipOnRateLimit = true
    }
    cli {
        enabled  = true  // route API calls through the local "gh" CLI, reusing its auth and higher limits (default false)
        fallback = true  // fall back to HTTP if gh is unavailable or a call fails (default true)
    }
}
```

## License

[![Apache License 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
