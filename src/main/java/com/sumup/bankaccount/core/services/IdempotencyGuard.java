package com.sumup.bankaccount.core.services;

import com.sumup.bankaccount.core.exceptions.IdempotencyKeyReusedException;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransactionType;
import com.sumup.bankaccount.core.ports.secondary.TransactionRepository;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Finds the earlier result of a retried request.
 *
 * <p>Callers must lock the affected accounts first. A retry that arrives while the first request
 * is still running then waits on that lock, and after the first request commits this lookup sees
 * its stored transaction (PostgreSQL READ COMMITTED reads fresh data on every statement).
 */
@Component
class IdempotencyGuard {

  private final TransactionRepository transactions;

  IdempotencyGuard(TransactionRepository transactions) {
    this.transactions = transactions;
  }

  Optional<Transaction> findEarlierResult(
      @Nullable String idempotencyKey,
      TransactionType type,
      @Nullable UUID sourceAccountId,
      UUID targetAccountId,
      long amount
  ) {
    if (idempotencyKey == null) {
      return Optional.empty();
    }
    return transactions.findByIdempotencyKey(idempotencyKey)
        .map(earlier -> {
          if (!earlier.isSameRequestAs(type, sourceAccountId, targetAccountId, amount)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
          }
          return earlier;
        });
  }
}
