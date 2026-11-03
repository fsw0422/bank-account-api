package com.sumup.bankaccount.adapters.secondary.postgres;

import static com.sumup.bankaccount.adapters.secondary.postgres.jooq.Tables.TRANSACTIONS;

import com.sumup.bankaccount.adapters.secondary.postgres.jooq.tables.records.TransactionsRecord;
import com.sumup.bankaccount.core.exceptions.IdempotencyKeyConflictException;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransactionType;
import com.sumup.bankaccount.core.ports.secondary.TransactionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** Transactions (ledger) table access with jOOQ. Takes part in the caller's Spring database transaction. */
@Repository
class JooqTransactionRepository implements TransactionRepository {

  private final DSLContext dsl;

  JooqTransactionRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(Transaction transaction) {
    try {
      dsl.insertInto(TRANSACTIONS)
          .set(TRANSACTIONS.ID, transaction.id())
          .set(TRANSACTIONS.TYPE, transaction.type().name())
          .set(TRANSACTIONS.SOURCE_ACCOUNT_ID, transaction.sourceAccountId())
          .set(TRANSACTIONS.TARGET_ACCOUNT_ID, transaction.targetAccountId())
          .set(TRANSACTIONS.AMOUNT, transaction.amount())
          .set(TRANSACTIONS.CURRENCY, transaction.currency().getCurrencyCode())
          .set(TRANSACTIONS.IDEMPOTENCY_KEY, transaction.idempotencyKey())
          .set(TRANSACTIONS.CREATED_AT, OffsetDateTime.ofInstant(transaction.createdAt(), ZoneOffset.UTC))
          .execute();
    } catch (DuplicateKeyException duplicate) {
      // Spring Boot's jOOQ setup turns SQL errors into Spring's DataAccessException types.
      // The only unique column besides the random UUID primary key is idempotency_key.
      var key = transaction.idempotencyKey();
      if (key == null) {
        throw duplicate;
      }
      throw new IdempotencyKeyConflictException(key);
    }
  }

  @Override
  public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
    return dsl.selectFrom(TRANSACTIONS)
        .where(TRANSACTIONS.IDEMPOTENCY_KEY.eq(idempotencyKey))
        .fetchOptional(JooqTransactionRepository::toTransaction);
  }

  private static Transaction toTransaction(TransactionsRecord row) {
    return new Transaction(
        row.getId(),
        TransactionType.valueOf(row.getType()),
        row.getSourceAccountId(),
        row.getTargetAccountId(),
        row.getAmount(),
        Currency.getInstance(row.getCurrency()),
        row.getIdempotencyKey(),
        row.getCreatedAt().toInstant());
  }
}
