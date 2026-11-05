# 17. Java over Kotlin

- **Status:** Accepted
- **Type:** Technical
- **Requirements:** none directly. It shapes all the code.

## Context

The service runs on the JVM with Spring Boot ([decision 14](14-runtime-and-operations.md)). Java
and Kotlin are both well supported there. Kotlin is the usual choice for its coroutines and its
concise data classes.

## Decision

Write the service in **Java** (Java 25).

## Why

- **Cheap waiting without coroutines.** Coroutines are the main reason to pick Kotlin. Java now has
  virtual threads, which give the same cheap waiting with plain blocking code. They are not enabled
  yet, but turning them on is one setting ([decision 16](16-virtual-threads-not-enabled.md)).
- **Concise data types without data classes.** Java has `record` now. `Account` and `Transaction`
  are records.

## Consequences

- Plain blocking code throughout: no `suspend` functions and no `Dispatchers.IO` around JDBC calls.
- Some Kotlin conveniences stay unavailable, such as null safety in the type system and extension
  functions.

## Alternatives considered

- **Kotlin with coroutines:** concise and familiar, with mature structured concurrency and
  cancellation. Blocking JDBC and jOOQ calls would need `Dispatchers.IO`, and `suspend` spreads
  through every layer. The comparison with virtual threads is in
  [decision 16](16-virtual-threads-not-enabled.md#alternatives-considered).
