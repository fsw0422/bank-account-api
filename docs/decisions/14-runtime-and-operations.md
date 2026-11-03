# 14. Runtime and operations

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** the brief's "could realistically run in production"

## Context

A new service should start on current, supported versions, and it should be easy to run and watch.

## Decision

- **Versions:** Java 25 (the latest LTS release), Spring Boot 4.1, PostgreSQL 18, and Gradle 9.8.
  Libraries: Flyway, jOOQ 3.21, and OpenAPI Generator 7.14. For tests: JUnit 5, Testcontainers,
  and ArchUnit.
- **A separate management port** (`9090`) for health and metrics. The API stays on `8080`.
- **Readiness includes the database check. Liveness does not.**
- **JSON logs** (ECS format) when `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`, which Compose sets. One
  `INFO` line per opened account, deposit, transfer, and replay.
- **Graceful shutdown,** and a container that runs a JRE only, as a non-root user.

The operations table is in [architecture.md](../architecture.md#operations).

## Why

- Current, supported versions for a new service. Java 27 is newer, but it is not an LTS release.
- Health and metrics stay off the public port.
- A database outage takes the service out of rotation without restart loops.

## Consequences

- One more port to configure.
- Running Gradle directly needs JDK 17 or newer, and Gradle then downloads JDK 25 if it is missing.
  The make targets that start, build, or test the service need no JDK
  ([decision 15](15-docker-only-local-setup.md)).

## Alternatives considered

- **Java 21:** also LTS and more widely installed. Java 25 was chosen as the newest LTS, and the
  toolchain and Docker make the host's Java version unimportant.
- **Health on the API port:** simpler, but it exposes management endpoints publicly.
