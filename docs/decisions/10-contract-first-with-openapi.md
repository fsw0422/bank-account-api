# 10. Contract first with OpenAPI

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** [API rules](../requirements.md#api-rules)

## Context

The HTTP contract, its documentation, and the server code can drift apart when each is written by
hand.

## Decision

- The OpenAPI file [`api.yml`](../../src/main/resources/static/api.yml) **defines the HTTP API**.
  The running service serves it at `/api.yml`.
- **OpenAPI Generator** (`spring`, interfaces only) generates `AccountsApi`, `TransfersApi`, and the
  request and response models on every build. The controllers implement those interfaces.
- Generator settings worth knowing:
  - `useSpringBuiltInValidation` keeps bad headers a `400`. The class-level `@Validated` it would
    otherwise add turns them into `500`s.
  - No Swagger annotations, and no `JsonNullable` (it needs Jackson 2).
  - Timestamps map to `Instant`, as in the domain.
  - Spring's deprecated `@Nullable` is replaced with JSpecify's.

## Why

- One machine-readable contract for clients, documentation, and the server.
- Routes, parameter binding, and field validation come from the contract, so they cannot drift from
  it.

## Consequences

- A build step, and generated models are mutable classes, not records.
- Rules a schema cannot express use vendor extensions: `x-field-extra-annotation` adds `@NotBlank`
  and the core's `@SupportedCurrency`.
- Error bodies are built by Spring's `ProblemDetail`, so the `Problem` schema is kept in step by
  hand.

## Alternatives considered

- **Code first** (for example springdoc generating the contract from the controllers): less to
  write, but the contract becomes a by-product of the code.
- **Hand-written controllers and a separate document:** the two drift apart.
