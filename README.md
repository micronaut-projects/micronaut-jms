# Micronaut JMS

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.jms/micronaut-jms-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.jms%22%20AND%20a:%22micronaut-jms-core%22)
[![Build Status](https://github.com/micronaut-projects/micronaut-jms/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-jms/actions)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micronaut.io/scans)

Micronaut JMS integration Micronaut and Java Messaging Service (JMS)

## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-jms/latest/guide/) for more information.

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-jms/snapshot/guide/) for the current development docs.

## Snapshots and Releases

Snapshots are automatically published to [JFrog OSS](https://oss.jfrog.org/artifactory/oss-snapshot-local/) using [Github Actions](https://github.com/micronaut-projects/micronaut-jms/actions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to JCenter and Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-jms/actions).

A release is performed with the following steps:

* [Edit the version](https://github.com/micronaut-projects/micronaut-jms/edit/master/gradle.properties) specified by `projectVersion` in `gradle.properties` to a semantic, unreleased version. Example `1.0.0`
* [Create a new release](https://github.com/micronaut-projects/micronaut-jms/releases/new). The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-jms/actions?query=workflow%3ARelease) to check it passed successfully.
* Celebrate!
