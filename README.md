# spinnsyn-backend

This project contains the application code and infrastructure for spinnsyn-backend.

## Technologies used

* Kotlin
* Spring Boot
* Gradle

## Getting started

### Getting github-package-registry packages NAV-IT

Some packages used in this repo are uploaded to the Github Package Registry which requires authentication:

```groovy
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```sh
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or directly on the command line:

```sh
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Building the application

#### Compile and package application

To build locally and run the integration tests you can simply run `./gradlew build`.

## Contact

### For NAV employees

We are available at the Slack channel `#flex`.
