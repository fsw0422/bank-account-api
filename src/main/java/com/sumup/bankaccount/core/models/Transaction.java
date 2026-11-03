package com.sumup.bankaccount.core.models;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A ledger entry: one deposit or one transfer that has happened. Never changed after it is stored.
 *
 * @param sourceAccountId the account money left, or {@code null} for a deposit
 * @param amount in minor units of {@code currency}, always positive
 * @param idempotencyKey the client's key for safe retries, if one was sent
 */
public record Transaction(
    UUID id,
    TransactionType type,
    @Nullable UUID sourceAccountId,
    UUID targetAccountId,
    long amount,
    Currency currency,
    @Nullable String idempotencyKey,
    Instant createdAt
) {

  public Transaction {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(targetAccountId, "targetAccountId");
    Objects.requireNonNull(currency, "currency");
    Objects.requireNonNull(createdAt, "createdAt");
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    if ((type == TransactionType.DEPOSIT) != (sourceAccountId == null)) {
      throw new IllegalArgumentException("only transfers have a source account");
    }
  }

  public static Transaction deposit(
      UUID accountId, long amount, Currency currency, @Nullable String idempotencyKey, Instant now) {
    return new Transaction(
        UUID.randomUUID(), TransactionType.DEPOSIT, null, accountId, amount, currency, idempotencyKey, now);
  }

  public static Transaction transfer(
      UUID sourceAccountId,
      UUID targetAccountId,
      long amount,
      Currency currency,
      @Nullable String idempotencyKey,
      Instant now
  ) {
    return new Transaction(
        UUID.randomUUID(), TransactionType.TRANSFER, sourceAccountId, targetAccountId, amount, currency,
        idempotencyKey, now);
  }

  /** Tells whether a retried request asks for exactly what this entry already did. */
  public boolean isSameRequestAs(
      TransactionType otherType, @Nullable UUID otherSourceAccountId, UUID otherTargetAccountId, long otherAmount) {
    return type == otherType
        && Objects.equals(sourceAccountId, otherSourceAccountId)
        && targetAccountId.equals(otherTargetAccountId)
        && amount == otherAmount;
  }
}
