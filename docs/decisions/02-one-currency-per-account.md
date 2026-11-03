# 02. One currency per account, no exchange

- **Status:** Accepted
- **Type:** Business
- **Requirements:** [FR-1](../requirements.md#fr-1-open-an-account), [FR-4](../requirements.md#fr-4-transfer-money)

## Context

Money can only move exactly between amounts of the same currency. Converting between currencies
needs exchange rates, rounding rules, and usually fees.

## Decision

- Each account has **one currency**, chosen when it is opened. It never changes.
- A deposit is always in the account's currency.
- A transfer needs both accounts to use the **same currency**. There is no currency exchange.

## Why

Keeps every money movement exact and simple. Exchange is a product of its own.

## Consequences

A transfer between currencies fails with `422 CURRENCY_MISMATCH`.

## Alternatives considered

- **Exchange at transfer time:** needs a rate source, rounding, fees, and an audit of the rate used.
- **Multi-currency accounts** (one balance per currency): more flexible, but every operation then
  needs a currency, and balances become a map.
