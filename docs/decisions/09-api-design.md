# 09. API design: routes, status codes, IDs, and timestamps

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** [FR-1](../requirements.md#fr-1-open-an-account) to [FR-4](../requirements.md#fr-4-transfer-money), [API rules](../requirements.md#api-rules)

## Context

The routes, status codes, and identifiers are the first thing a client sees, and the hardest to
change later.

## Decision

- **Resource-style routes.** A deposit belongs to one account, so it is a sub-resource:
  `POST /accounts/{accountId}/deposits`. A transfer involves two accounts, so it is top-level:
  `POST /transfers`.
- **`POST` returns `201 Created`** with the stored account or transaction. Only account creation has
  a `Location` header, because no endpoint reads a single transaction yet.
- **IDs are random UUIDs** created by the service.
- **Timestamps come from the service clock**, in UTC, cut to microseconds.

## Why

- The URLs read like the domain.
- Random UUIDs are not guessable and need no central counter.
- PostgreSQL stores microseconds, so a timestamp returned at creation equals the one returned by a
  later read.

## Consequences

- Deposits and transfers have no `Location` header until a read endpoint exists.
- Random IDs give worse index locality than time-ordered ones.

## Alternatives considered

- **`POST /accounts/{id}/transfers`:** hides that a transfer has two equal sides.
- **Time-ordered IDs (UUIDv7)** or database sequences: better index locality. Sequences are
  guessable, and UUIDv7 is listed as a next step.
