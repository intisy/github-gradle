# Github Gradle

## What is Github Gradle?

Github Gradle makes it easier for the average person to make personal dependencies without spending money on websites like jitpack, because it allows you to make dependencies out of you GitHub projects assets just like JitPack 

## Usage

You can add the plugin like this and add as many codeartifact repositories, as you want:

```groovy
plugins {
    id 'io.github.intisy.github-gradle'
}

repositories {
    codeartifact {
        region '<region>'
        domain '<domain>'
        domainOwner '<domainOwner>'
        repository '<repository>'
    }
}
```

Or to add repositories to the Publishing plugin:

```groovy
plugins {
    id 'maven-publish'
    id 'io.github.intisy.github-gradle'
}

publishing {
    repositories {
        codeartifact {
            region '<region>'
            domain '<domain>'
            domainOwner '<domainOwner>'
            repository '<repository>'
        }
    }
}
```

Once you have the plugin installed you can use it like so:

```groovy
dependencies {
    githubImplementation "USERNAME:REPOSITORY:TAG"
}
```

Which in my case would be:

```groovy
dependencies {
    githubImplementation "intisy:SimpleLogger:1.12.7"
}
```

## License

[![Apache License 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
