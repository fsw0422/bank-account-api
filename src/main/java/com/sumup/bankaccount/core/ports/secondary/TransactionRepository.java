package com.sumup.bankaccount.core.ports.secondary;

import com.sumup.bankaccount.core.models.Transaction;
import java.util.Optional;

public interface TransactionRepository {

  /**
   * @throws com.sumup.bankaccount.core.exceptions.IdempotencyKeyConflictException if another
   *     transaction already stored the same idempotency key
   */
  void insert(Transaction transaction);

  Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
