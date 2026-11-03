# 11. Hexagonal architecture

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** all. The layout is described in [architecture.md](../architecture.md#hexagonal-architecture).

## Context

The business rules (money, locking, retries) are the part worth protecting. HTTP and SQL details
change more often, and mixing them in makes the rules hard to test and to read.

## Decision

- A **hexagonal layout** (ports and adapters). The core holds the domain and the use cases.
  *Primary* ports and adapters drive the core (the REST API). *Secondary* ports and adapters are
  driven by it (PostgreSQL).
- **ArchUnit** (`ArchitectureTest`) enforces it:
  - the core never depends on adapters;
  - the core uses no web, HTTP, JDBC, servlet, Jackson, jOOQ, `java.sql`, or Hibernate code;
  - primary and secondary adapters never use each other.
- The core **may** use the Bean Validation API (`jakarta.validation`) to define constraints, but
  not its implementation ([decision 12](12-validation-at-the-edge-rules-in-the-core.md)).
- **Database transaction boundaries sit on the core services** (`@Transactional`), because the use
  case decides what must be atomic.

## Why

- The business rules stay free of HTTP and SQL, and they are easy to unit test without Spring.
- A broken boundary fails the build instead of slipping in unnoticed.

## Consequences

- More packages and interfaces than a simple layered design.
- The core uses a few Spring annotations (`@Service`, `@Component`, `@Transactional`) and the Bean
  Validation API. This is a deliberate, pragmatic exception.

## Alternatives considered

- **A classic layered design** (controller, service, repository): fewer files, but nothing keeps SQL
  or HTTP types out of the business rules.
- **A transaction port, or a decorator in an adapter:** keeps Spring out of the core completely,
  at the cost of more code.
