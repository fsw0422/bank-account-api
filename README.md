# Bank Account API

A REST API to create bank accounts, deposit money, and transfer money between accounts.

This README explains how to start the service locally. What it does is in
[`docs/requirements.md`](docs/requirements.md), how it is built is in
[`docs/architecture.md`](docs/architecture.md), and why is in [`docs/decisions/`](docs/decisions/README.md).

## Requirements

- **Docker** with Docker Compose v2. This is all you need to start the service.
- **JDK 17 or newer**, only to start it from an IDE or with Gradle. Gradle downloads JDK 25 itself
  if it is missing.

All dependencies come from Maven Central. No credentials needed.

## Start with Docker

```bash
make up      # build the image, start the API and PostgreSQL, wait until both are healthy
make smoke   # check that it works: a curl walkthrough of every endpoint
make logs    # follow the API logs
make test    # run all tests, inside Docker
make down    # stop everything and delete the local database
```

Without `make`: `docker compose up --build`, then `./scripts/smoke.sh` in another terminal.
Run `make` on its own to list all targets.

| URL | What |
|---|---|
| <http://localhost:8080> | API |
| <http://localhost:8080/api.yml> | OpenAPI contract |
| <http://localhost:9090/actuator/health/readiness> | Readiness, including the database check |
| <http://localhost:9090/actuator/prometheus> | Metrics |

If a port is taken, choose others:

```bash
APP_PORT=8081 MANAGEMENT_PORT=9091 make up
BASE_URL=http://localhost:8081 MANAGEMENT_URL=http://localhost:9091 make smoke
```

## Start from the IDE

Run `TestBankAccountApplication` (under `src/test`) or `./gradlew bootTestRun`. It starts
PostgreSQL in a container for you, so Compose is not needed. Docker must still be running.
The API and health endpoints use the same ports as above.
