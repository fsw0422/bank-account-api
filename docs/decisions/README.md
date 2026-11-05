# Decision records

Each notable decision, business or technical, has its own file: why it was made, what it costs,
and what else was considered. The requirements they serve are in
[requirements.md](../requirements.md), and the resulting structure is in
[architecture.md](../architecture.md).

| # | Decision | Type | In one line |
|---|---|---|---|
| [01](01-money-in-minor-units.md) | Money as whole minor units | Business | Exact integers. Decimals are rejected, and only currencies with defined decimals are allowed. |
| [02](02-one-currency-per-account.md) | One currency per account, no exchange | Business | Transfers need matching currencies. |
| [03](03-stored-balance-and-a-ledger.md) | Stored balance plus an append-only ledger | Business | One-row balance reads, and a ledger that explains every balance. |
| [04](04-idempotent-retries.md) | Safe retries with idempotency keys | Business | The key is stored with the money movement and checked after locking. |
| [05](05-no-authentication.md) | No authentication in this version | Business | Out of scope for the brief. |
| [06](06-consistency-under-concurrent-requests.md) | Consistency under concurrent requests | Technical | One database transaction, row locks in a fixed order, checks before writes, database constraints. |
| [07](07-isolation-level-read-committed.md) | Isolation level `READ COMMITTED` | Technical | With row locks it is enough. Stricter levels make waiting requests fail. |
| [08](08-error-responses.md) | Error responses | Technical | RFC 9457 with a `code`. `400` for bad requests, `422` for broken rules, `503` for outages. |
| [09](09-api-design.md) | API design: routes, status codes, IDs, and timestamps | Technical | Resource-style routes, `201`, random UUIDs, UTC microseconds. |
| [10](10-contract-first-with-openapi.md) | Contract first with OpenAPI | Technical | `api.yml` generates the controller interfaces and models. |
| [11](11-hexagonal-architecture.md) | Hexagonal architecture | Technical | Primary and secondary ports and adapters, enforced by ArchUnit. |
| [12](12-validation-at-the-edge-rules-in-the-core.md) | Validation at the edge, business rules once in the core | Technical | Field errors at the edge. Each business rule is defined once, in the core. |
| [13](13-postgresql-flyway-and-jooq.md) | PostgreSQL, Flyway, and jOOQ | Technical | Visible, compiler-checked SQL, with generated classes committed. |
| [14](14-runtime-and-operations.md) | Runtime and operations | Technical | Java 25 LTS, Spring Boot 4.1, a management port, readiness that checks the database. |
| [15](15-docker-only-local-setup.md) | Docker-only local setup | Technical | Start, build, and test need only Docker. No database password. |
| [16](16-virtual-threads-not-enabled.md) | Virtual threads not enabled | Technical | The 10 database connections are the limit, not threads. Enable with a concurrency limit once requests wait outside the database. |
| [17](17-java-over-kotlin.md) | Java over Kotlin | Technical | Virtual threads replace coroutines, and records replace data classes. |

## Template

New records follow the same structure:

```markdown
# NN. Title

- **Status:** Proposed, Accepted, or Replaced by NN
- **Type:** Business or Technical
- **Requirements:** the FR-n it serves

## Context
## Decision
## Why
## Consequences
## Alternatives considered
## Evidence          (only when something was measured)
```
