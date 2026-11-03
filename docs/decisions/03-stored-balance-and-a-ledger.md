# 03. Stored balance plus an append-only ledger

- **Status:** Accepted
- **Type:** Business
- **Requirements:** [FR-1](../requirements.md#fr-1-open-an-account), [FR-3](../requirements.md#fr-3-deposit-money), [FR-4](../requirements.md#fr-4-transfer-money), [FR-5](../requirements.md#fr-5-safe-under-concurrent-requests)

## Context

Clients read balances often. The bank must also be able to explain every balance.

## Decision

- Each account **stores its current balance**.
- Every deposit and transfer is also stored as a **transaction**, which is never changed or
  deleted. All transactions together are the **ledger**.
- A positive initial balance is stored as an **opening deposit**, so the ledger explains every
  balance from the start.
- **Naming:** a stored deposit or transfer is a *transaction*, everywhere: in the code
  (`Transaction`), the API (`TransactionResponse`), and the table (`transactions`). The database
  concept is always written *database transaction*.

## Why

- Reading a balance is one row.
- The ledger is an audit trail: for every account, money in minus money out equals the balance.
- One name per concept avoids two words for the same thing.

## Consequences

- Every operation writes twice (balance and ledger). One database transaction keeps them in step
  ([decision 06](06-consistency-under-concurrent-requests.md)).
- No automated test checks that each balance equals its ledger total
  ([FR-5](../requirements.md#fr-5-safe-under-concurrent-requests), item 4).
- "Transaction" has two meanings in this field. The text always says "database transaction" for
  the second one.

## Alternatives considered

- **Compute balances from the ledger:** always consistent, but every read grows with the history
  unless snapshots are added.
- **Full double-entry bookkeeping:** a debit line and a credit line per movement, as accountants
  do. Here one row holds both the source and the target, which is enough for two kinds of movement.
