# 01. Money as whole minor units

- **Status:** Accepted
- **Type:** Business
- **Requirements:** [FR-1](../requirements.md#fr-1-open-an-account), [FR-3](../requirements.md#fr-3-deposit-money), [FR-4](../requirements.md#fr-4-transfer-money), [API rules](../requirements.md#api-rules)

## Context

Every amount and balance must be exact. Clients send amounts as JSON, and currencies have different
numbers of decimals: EUR has 2, JPY has 0, KWD has 3.

## Decision

- Amounts and balances are **whole numbers of minor units** in a 64-bit integer (`long` in Java,
  `BIGINT` in PostgreSQL). For EUR, `1050` means €10.50.
- A decimal amount such as `10.5` is **rejected with `400`**, never rounded
  (`spring.jackson.deserialization.accept-float-as-int: false`). A number outside the 64-bit range
  is rejected too.
- Only currencies with a **defined number of decimal places** are allowed: `EUR` and `JPY`, but not
  `XAU` (gold) or `XXX`. `Account.supportsCurrency` holds this rule
  ([decision 12](12-validation-at-the-edge-rules-in-the-core.md)).

## Why

- Integers are exact. There is no rounding, and the JSON stays simple.
- Rounding `10.5` silently to `10` would lose money.
- "An amount in minor units" has no meaning for a currency without minor units.

## Consequences

- Clients must know each currency's decimals to display amounts.
- The largest balance is 9,223,372,036,854,775,807 minor units. A deposit or transfer that would go
  past it fails with `422 BALANCE_LIMIT_EXCEEDED`.

## Alternatives considered

- **`BigDecimal` or decimal strings:** natural for people to read, but every currency then needs a
  rounding rule, and the JSON is harder to validate.
- **A money library** (JSR 354 Moneta, Joda-Money): a proper money type, but an extra dependency
  that this service does not need yet. The JDK's `java.util.Currency` already knows each
  currency's decimals.
