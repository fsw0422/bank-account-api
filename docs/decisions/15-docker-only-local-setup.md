# 15. Docker-only local setup

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** the brief's "Runnable, Buildable, Testable, Portable"

## Context

The service must run on a machine that is not the author's: no hardcoded paths, no private
registries, no credentials, and as few tools to install as possible.

## Decision

- **Docker is the only requirement** for the make targets that start, build, or test the service:
  - `make up` and `make run` build the image with Gradle inside Docker, and run the API and
    PostgreSQL in containers.
  - `make test` and `make build` run Gradle in a JDK container (`scripts/gradle-in-docker.sh`).
  - `make smoke` needs only bash and curl.
- **Tests inside a container use the host's Docker.** The container shares the Docker socket, and
  the tests reach the PostgreSQL that Testcontainers starts through `host.docker.internal`
  (Testcontainers calls this the "Docker wormhole"). The project is copied into the container, so
  the checkout stays untouched. Gradle's downloads stay in the Docker volume
  `bank-account-api-gradle`.
- **The local database has no password and no published port.** It uses trust auth, and only the
  API container can reach it. The repository holds no secrets.
- **No `.sdkmanrc`.** The Gradle toolchain is the single place that says "Java 25".

## Why

- A reviewer can run everything with one tool installed.
- No password exists, so none can leak.

## Consequences

- **A JDK 17 or newer is still needed** to start the app from an IDE, to run `./gradlew` directly,
  and for `make jooq`, which runs Gradle on the host.
- The test container needs `/var/run/docker.sock`. Rootless Docker, which puts the socket
  elsewhere, is not supported yet.
- The first `make test` downloads Gradle and the dependencies. Later runs take seconds.
- Trust auth is only acceptable locally. Real deployments use password auth with a secret from a
  secrets manager.

## Alternatives considered

- **Docker-in-Docker:** fully isolated, but needs a privileged container.
- **Requiring a local JDK:** simpler scripts, but one more thing for a reviewer to install.
- **A database password in `docker-compose.yml`:** common, but it puts a credential in the
  repository.
