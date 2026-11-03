# 12. Validation at the edge, business rules once in the core

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** [FR-1](../requirements.md#fr-1-open-an-account), [Error responses](../requirements.md#error-responses)

## Context

Clients need clear `400` field errors. The core must also stay safe whichever adapter calls it,
including future ones such as a message consumer.

## Decision

- **At the web edge,** Bean Validation rejects bad input with `400` field errors. The constraints
  come from `api.yml` ([decision 10](10-contract-first-with-openapi.md)).
- **In the domain,** objects also refuse impossible values, such as a negative balance or an amount
  of zero. Reaching such a check means a bug, which returns `500`.
- **A business rule is written once, in the core, and the edge reuses it.** For example,
  `Account.supportsCurrency` decides which currencies are allowed. The core's `@SupportedCurrency`
  constraint (`core/validation/`) applies the same rule to the request's currency text, and the
  `Account` constructor enforces it.
- Rules that are only about the input format, such as JSON types, stay in the web adapter.

## Why

- Clear field errors for clients.
- A core that stays correct whichever adapter calls it.
- One definition per rule, so the edge and the core cannot drift apart.

## Consequences

- Some rules are checked twice: at the edge for a good error message, and in the core for safety.
- **Known gap:** the owner-name rules (not blank, at most 100 characters) are still only checked at
  the edge.

## Alternatives considered

- **Validate only at the edge:** simpler, but a second adapter could bypass the rules. Before this
  decision, the core accepted `XAU` from any caller other than the REST API.
- **Validate only in the core and map its errors to `400`:** one place, but field-level error
  details would need to be rebuilt by hand.
