# 04. Safe retries with idempotency keys

- **Status:** Accepted
- **Type:** Business
- **Requirements:** [FR-6](../requirements.md#fr-6-safe-retries-idempotency)

## Context

Networks fail. A client that gets no response does not know whether money moved. If it simply sends
the request again, money can move twice.

## Decision

- Deposits and transfers accept an optional **`Idempotency-Key`** header (1 to 255 characters).
- The key is **stored on the transaction row**, in a unique column. There is no separate key store.
- The service looks the key up **after locking the accounts**. A retry that arrives while the first
  request runs therefore waits for it, and then finds its result
  ([decision 07](07-isolation-level-read-committed.md)).
- The same key with the same request returns the original transaction with `201`. The same key
  with a different request gets `422 IDEMPOTENCY_KEY_REUSED`. This follows the IETF
  `Idempotency-Key` header draft.
- If two requests with one key touch different accounts, they do not wait for each other. The
  unique column rejects the second insert, and that client gets `409 IDEMPOTENCY_KEY_CONFLICT`.
- Only successful requests are remembered. Keys are global and kept forever.

## Why

- The key is stored atomically with the money movement, so a key never exists without its money
  movement, or the other way round.
- No extra infrastructure, such as a key table with expiry or a cache.

## Consequences

- Only deposits and transfers support keys. Account opening does not.
- Keys are global, because there are no clients or logins yet, and they never expire.
- A replay returns the stored transaction, not a byte-for-byte copy of the first response.
- When a commit fails, the outcome is unknown. Retrying is then only safe with the same key
  ([decision 08](08-error-responses.md)).

## Alternatives considered

- **A separate idempotency store** that keeps whole responses and expires them: the usual choice
  for a public API, but it must be written in the same database transaction to stay atomic.
- **The key in the request body:** works, but the key is about the request, not the resource, so a
  header fits better.
