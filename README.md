# Github Gradle

Archives containing JAR files are available as [releases](https://github.com/intisy/github-gradle/releases).

## What is github-gradle?

GitHub Gradle implements a way to get dependencies from a GitHub asset, so you don't need services like jitpack anymore

## Usage

Using the plugins DSL:

```groovy
plugins {
    id "io.github.intisy.github-gradle" version "1.10.2"
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
        classpath "io.github.intisy.github-gradle:1.10.2"
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

### Sources: git repositories and direct jars

Beyond a GitHub release, a dependency can also be resolved by cloning and building an arbitrary
git repository, or by downloading a jar directly over HTTP(S). Both are declared in a nested
`sources { }` block inside `github { }`, and both `git { }` and `jar { }` are repeatable:

```groovy
github {
    sources {
        git {
            url = "https://gitlab.com/me/lib.git"
            ref = "main"                  // branch, tag or commit; optional, default the remote's default branch
            dir = "java"                  // gradle project directory; optional, default the checkout root
            modules = "routing contracts" // modules whose jars to take; optional, default the root project's jar
            into = "implementation"       // native configuration; optional, default "implementation"
        }
        jar {
            url = "https://nexus.internal/libs/foo-1.0.jar"
            header "Authorization", "Bearer ${myToken}"
            sha256 = "80a981f3202da20cc46a0bf22e6e0ff40803e857ba6f4571496805c079162ffc" // optional; verified after download
            into = "implementation"
        }
    }
}
```

`git { }` clones any git host, not just github.com, checks out `ref`, builds it with its own
Gradle wrapper, and caches the result by resolved commit. `dir` moves the build to a repository
whose Gradle root is a subdirectory rather than the checkout root, and `modules` names the modules
of a multi-module build whose jars to take, one cached jar each. One clone and one build serve
every module. Together they let a library with several consumable modules be consumed straight from
a branch, with no release to cut for each change. `jar { }` downloads a jar with optional
request headers (for a private Nexus/Artifactory/S3-backed host) and an optional expected
`sha256`; a mismatch fails the build instead of silently using the wrong jar. A jar reachable
through more than one of the `github*` coordinates, `sources { git { } }`, or `sources { jar { } }`
is only ever added to the native configuration once.

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

## Using the library without Gradle

Cloning repositories, resolving releases and downloading assets are also published as a small,
Gradle-free library, separate from the plugin jar:

```groovy
dependencies {
    implementation "io.github.intisy:github-gradle-api:1.3.8"
}
```

The entry point is `GitHubApi.create(...)`. It needs a `GitHubConfig` (the access token and
auth/cli/resilience settings); `GitHubConfig.builder()` assembles one without any Gradle DSL,
every builder method is optional, and calling `build()` with none produces a config for fully
anonymous, unauthenticated access:

```java
import io.github.intisy.gradle.github.api.*;
import io.github.intisy.gradle.github.api.config.*;

import java.io.File;
import java.util.Collections;

GitHubConfig config = GitHubConfig.builder()
        .token(System.getenv("GITHUB_TOKEN"))
        .build();

GitHubApi api = GitHubApi.create(config, new ResourceSettings());

// A GitHub release
File releaseJar = api.releases().downloadJar("intisy", "simple-logger", "1.12.7")
        .orElseThrow(() -> new IllegalStateException("jar not found"));

// An arbitrary git repository, cloned and built
File gitJar = api.sourceBuilds().buildFromGit("https://gitlab.com/me/lib.git", "main");

// A direct jar URL, with an optional header and sha256 check
File urlJar = api.downloads().download("https://nexus.internal/libs/foo-1.0.jar",
        Collections.singletonMap("Authorization", "Bearer " + System.getenv("NEXUS_TOKEN")),
        "80a981f3202da20cc46a0bf22e6e0ff40803e857ba6f4571496805c079162ffc");
```

`api.repositories()`, `api.publishing()`, `api.sourceBuilds()`, `api.downloads()` and
`api.resolver()` reach the same capabilities the plugin's own tasks use. `GitHubApi.create` also
accepts a `GitHubLogger` argument if you want diagnostics sent somewhere other than `System.err`,
and `GitHubApi.create()` with no arguments defaults to an anonymous config for quick,
unauthenticated use.

## License

[![Apache License 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
