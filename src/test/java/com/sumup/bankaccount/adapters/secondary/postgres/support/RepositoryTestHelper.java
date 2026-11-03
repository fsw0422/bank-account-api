package com.sumup.bankaccount.adapters.secondary.postgres.support;

import static com.sumup.bankaccount.adapters.secondary.postgres.jooq.Tables.ACCOUNTS;
import static com.sumup.bankaccount.adapters.secondary.postgres.jooq.Tables.TRANSACTIONS;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

/**
 * Direct table access for the repository tests. The "given" methods are plain inserts that set up
 * a state. The "all" methods read a whole table to check the final state. They never use the
 * repositories under test, so a query is never checked by itself, and they stay as simple as
 * possible so they cannot hide a bug.
 */
public final class RepositoryTestHelper {

  /** Microsecond precision, as PostgreSQL stores it, so values read back compare equal. */
  public static final Instant CREATED_AT = Instant.parse("2026-11-02T10:15:30.123456Z");

  /** One row of the accounts table, as stored. */
  public record AccountRow(UUID id, String ownerName, String currency, long balance, Instant createdAt) {

    public AccountRow withBalance(long newBalance) {
      return new AccountRow(id, ownerName, currency, newBalance, createdAt);
    }
  }

  /** One row of the transactions table, as stored. */
  public record TransactionRow(
      UUID id,
      String type,
      @Nullable UUID sourceAccountId,
      UUID targetAccountId,
      long amount,
      String currency,
      @Nullable String idempotencyKey,
      Instant createdAt
  ) {}

  private RepositoryTestHelper() {}

  public static AccountRow accountRow(long balance) {
    return new AccountRow(UUID.randomUUID(), "Owner", "EUR", balance, CREATED_AT);
  }

  public static void deleteAllRows(DSLContext dsl) {
    // Transactions first: they reference accounts.
    dsl.deleteFrom(TRANSACTIONS).execute();
    dsl.deleteFrom(ACCOUNTS).execute();
  }

  public static void givenAccount(DSLContext dsl, AccountRow row) {
    dsl.insertInto(ACCOUNTS)
        .set(ACCOUNTS.ID, row.id())
        .set(ACCOUNTS.OWNER_NAME, row.ownerName())
        .set(ACCOUNTS.CURRENCY, row.currency())
        .set(ACCOUNTS.BALANCE, row.balance())
        .set(ACCOUNTS.CREATED_AT, OffsetDateTime.ofInstant(row.createdAt(), ZoneOffset.UTC))
        .execute();
  }

  public static void givenTransaction(DSLContext dsl, TransactionRow row) {
    dsl.insertInto(TRANSACTIONS)
        .set(TRANSACTIONS.ID, row.id())
        .set(TRANSACTIONS.TYPE, row.type())
        .set(TRANSACTIONS.SOURCE_ACCOUNT_ID, row.sourceAccountId())
        .set(TRANSACTIONS.TARGET_ACCOUNT_ID, row.targetAccountId())
        .set(TRANSACTIONS.AMOUNT, row.amount())
        .set(TRANSACTIONS.CURRENCY, row.currency())
        .set(TRANSACTIONS.IDEMPOTENCY_KEY, row.idempotencyKey())
        .set(TRANSACTIONS.CREATED_AT, OffsetDateTime.ofInstant(row.createdAt(), ZoneOffset.UTC))
        .execute();
  }

  public static List<AccountRow> allAccounts(DSLContext dsl) {
    return dsl.selectFrom(ACCOUNTS).fetch(row -> new AccountRow(
        row.getId(),
        row.getOwnerName(),
        row.getCurrency(),
        row.getBalance(),
        row.getCreatedAt().toInstant()));
  }

  public static List<TransactionRow> allTransactions(DSLContext dsl) {
    return dsl.selectFrom(TRANSACTIONS).fetch(row -> new TransactionRow(
        row.getId(),
        row.getType(),
        row.getSourceAccountId(),
        row.getTargetAccountId(),
        row.getAmount(),
        row.getCurrency(),
        row.getIdempotencyKey(),
        row.getCreatedAt().toInstant()));
  }
}
