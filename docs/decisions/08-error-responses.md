# 08. Error responses

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** [Error responses](../requirements.md#error-responses)

## Context

Clients need to tell a bad request from a broken business rule, and a temporary outage from a bug,
without parsing human-readable text.

## Decision

- Every error is an **RFC 9457 problem** (`application/problem+json`) with an extra machine-readable
  **`code`** field. Invalid fields are listed under `errors`.
- **`400`** means the request is wrong on its own: bad JSON, a missing field, a decimal amount.
  **`422`** means it is well formed but breaks a business rule: not enough money, different
  currencies. `404` and `409` keep their usual meaning.
- Business errors are **sealed exception classes** in `core/exceptions/`. `ApiExceptionHandler`
  maps them with an exhaustive `switch`.
- **Database outages return `503` with `Retry-After: 1`.** An outage is recognized by Spring's data
  access errors, and also by failures to open or roll back the database transaction, which is how a
  stopped database actually shows up.
- **Anything unexpected returns `500`** with no internal details. The log has them.

## Why

- One error format for every client. Clients branch on `code`.
- The compiler rejects a new business error that has no HTTP mapping.
- The client knows when a retry makes sense, and internals never leak.

## Consequences

- All business error types live in one package.
- Error details are only in the logs.
- A failed commit leaves the outcome unknown, so deposits and transfers need an `Idempotency-Key` to
  retry safely ([decision 04](04-idempotent-retries.md)).

## Alternatives considered

- **`409 Conflict` or `402 Payment Required` for insufficient funds:** both are used by real APIs.
  `422` was chosen because the request is valid but breaks a rule about the current state.
- **A custom error format:** no benefit over the standard, and clients would need to learn it.

## Evidence

A real outage was checked by stopping PostgreSQL in Compose. At first, requests got `500`, because
the failures to open or roll back the database transaction were not mapped. After the fix, requests
got `503` with `Retry-After: 1`, readiness turned `503` while liveness stayed `200`, and the service
recovered on its own once the database was back.
