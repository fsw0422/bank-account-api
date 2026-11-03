# 13. PostgreSQL, Flyway, and jOOQ

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** [FR-4](../requirements.md#fr-4-transfer-money), [FR-5](../requirements.md#fr-5-safe-under-concurrent-requests)

## Context

The service needs real database transactions and row locks. The SQL that takes those locks should
be visible and checked, not hidden behind a framework.

## Decision

- **PostgreSQL 18**, with the schema created by **Flyway** migrations at start-up.
- **jOOQ** for all queries, with table classes generated from the real schema. The row lock is
  written as `.forUpdate()`.
- The generated classes are **committed** in `jooq-generated/`. `make jooq` regenerates them from a
  throwaway PostgreSQL with no password, and CI fails if the committed classes are out of date.
  The steps are in [architecture.md](../architecture.md#generated-code).

## Why

- SQL stays visible in the code, including the lock. No hidden queries or lazy loading.
- The compiler checks table names, column names, and types.
- Committed classes mean building, including the Docker image, needs no database.

## Consequences

- Generated code must be regenerated after every migration. CI catches a forgotten run.
- Row mapping to domain objects is written by hand.
- `make jooq` runs Gradle on the host, so it needs a JDK
  ([decision 15](15-docker-only-local-setup.md)).

## Alternatives considered

- **JPA and Hibernate:** less SQL to write, but locking, flushing, and queries happen out of sight.
- **Plain SQL strings with `JdbcClient`** (the first version): visible, but a typo in a column name
  only fails at runtime.
- **Generating jOOQ classes from the SQL files without a database** (jOOQ's `DDLDatabase`): no
  container needed, but PostgreSQL-specific syntax such as the regular-expression checks may not
  parse.
