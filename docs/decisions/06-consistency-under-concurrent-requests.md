# 06. Consistency under concurrent requests

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** [FR-4](../requirements.md#fr-4-transfer-money), [FR-5](../requirements.md#fr-5-safe-under-concurrent-requests)

## Context

A transfer changes two balances and stores one transaction. Many requests can touch the same
accounts at the same time. The service must never lose an update, never let a balance go below
zero, and never deadlock.

## Decision

1. **One database transaction per operation.** Both balance updates and the stored transaction
   commit together or not at all. The use cases are `@Transactional`
   ([decision 11](11-hexagonal-architecture.md)).
2. **Pessimistic row locks.** Each operation locks the accounts it changes with
   `SELECT ... FOR UPDATE`. Other requests on the same account wait, so no request reads a stale
   balance and overwrites another's change.
3. **A fixed lock order.** A transfer locks its two accounts in one fixed order, using Java's UUID
   comparison. Any fixed order works, as long as every transfer uses the same one.
4. **All checks before any write.** Both new balances are computed and checked before either is
   saved.
5. **Database constraints as a safety net.** `CHECK (balance >= 0)`, `CHECK (amount > 0)`, the
   transfer-source rule, and the unique idempotency key reject bad data even if the code has a bug.

The isolation level that makes this enough is [decision 07](07-isolation-level-read-committed.md).
The full sequence is in [architecture.md](../architecture.md#a-transfer-step-by-step).

### Why a fixed lock order prevents deadlocks

Transfer T1 goes from A to B while transfer T2 goes from B to A:

- **Without a fixed order,** T1 locks A and T2 locks B. Then T1 waits for B, and T2 waits for A.
  That is a deadlock, and PostgreSQL aborts one of them.
- **With a fixed order,** both lock A first. T2 waits until T1 commits, then runs. No deadlock.

## Why

- Easy to reason about, and it behaves the same under heavy contention.
- The service needs no retry loop: a request that waits continues once the lock is free.
- Checking everything before writing means a failed rule never leaves a half-done change.

## Consequences

- Requests on one very busy account run one at a time. A merchant account that receives many
  payments would become a bottleneck.
- Every future operation that locks several accounts must use the same order.
- Some rules exist in two places, the code and the schema, and must stay in step.
- **Not covered by automated tests:** the parts are tested one by one (the lock order, the row
  lock, the constraints), but no test sends concurrent requests against PostgreSQL. Nothing
  automated checks, under load, that updates are not lost, that a drained account never goes
  negative, that opposite transfers do not deadlock, or that parallel retries with one key move
  money once.

## Alternatives considered

- **Optimistic version checks:** no waiting, but under contention many requests fail and must be
  retried.
- **`SERIALIZABLE` isolation:** see [decision 07](07-isolation-level-read-committed.md).
- **One conditional `UPDATE ... WHERE balance >= amount`:** enough for a single account, but a
  transfer also changes a second account and stores a transaction.
- **A queue per account:** removes lock contention, but adds infrastructure and makes the API
  asynchronous.

## Evidence

The code was broken on purpose to check that the tests notice:

| Change | Result |
|---|---|
| Lock the source account first instead of the smaller ID | `DefaultTransferServiceTest.locksTheSmallerIdFirstInBothDirections` fails. An earlier multi-threaded test (since removed) showed the effect: PostgreSQL reported `deadlock detected` and aborted 180 of 200 transfers. |
| Remove `FOR UPDATE` | `JooqAccountRepositoryTest.findByIdForUpdateLocksThatRowUntilTheDatabaseTransactionEnds` fails. The earlier multi-threaded test showed the effect: only 38 of 100 deposits survived, and 49 transfers "succeeded" where only 10 were covered by the balance. |
| `updateBalance` without its `WHERE` clause | `updateBalanceChangesOnlyThatAccount` and `updateBalanceFailsLoudlyForAnUnknownAccount` fail |
| A symmetric mapping bug: write `balance + 1`, read `balance - 1` | Three repository tests fail. A round-trip test (insert, then read through the same repository) would pass, which is why the tests check the tables directly. |
