# 05. No authentication in this version

- **Status:** Accepted for this version
- **Type:** Business
- **Requirements:** [What clients can do](../requirements.md#what-clients-can-do), [Out of scope](../requirements.md#out-of-scope)

## Context

The brief asks for accounts, deposits, and transfers. It says nothing about users or access rights.

## Decision

The API has **no authentication or authorization**. Every operation is open to anyone who can reach
the API.

## Why

It is out of scope for the brief, and it keeps the focus on the money rules.

## Consequences

- Anyone who can reach the API can move money. This version must not face the internet as it is.
- Idempotency keys are global, because there is no client to scope them to
  ([decision 04](04-idempotent-retries.md)).

## Next steps

Authentication (for example as an OAuth2 resource server), checks that a caller owns the account,
idempotency keys per client, and rate limiting.
