package com.sumup.bankaccount.adapters.secondary.postgres;

import static com.sumup.bankaccount.adapters.secondary.postgres.jooq.Tables.ACCOUNTS;

import com.sumup.bankaccount.adapters.secondary.postgres.jooq.tables.records.AccountsRecord;
import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.ports.secondary.AccountRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Accounts table access with jOOQ. Takes part in the caller's Spring database transaction. */
@Repository
class JooqAccountRepository implements AccountRepository {

  private final DSLContext dsl;

  JooqAccountRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(Account account) {
    dsl.insertInto(ACCOUNTS)
        .set(ACCOUNTS.ID, account.id())
        .set(ACCOUNTS.OWNER_NAME, account.ownerName())
        .set(ACCOUNTS.CURRENCY, account.currency().getCurrencyCode())
        .set(ACCOUNTS.BALANCE, account.balance())
        .set(ACCOUNTS.CREATED_AT, OffsetDateTime.ofInstant(account.createdAt(), ZoneOffset.UTC))
        .execute();
  }

  @Override
  public Optional<Account> findById(UUID accountId) {
    return dsl.selectFrom(ACCOUNTS)
        .where(ACCOUNTS.ID.eq(accountId))
        .fetchOptional(JooqAccountRepository::toAccount);
  }

  @Override
  public Optional<Account> findByIdForUpdate(UUID accountId) {
    // Row lock held until commit or rollback. Others locking this row wait; plain reads do not.
    return dsl.selectFrom(ACCOUNTS)
        .where(ACCOUNTS.ID.eq(accountId))
        .forUpdate()
        .fetchOptional(JooqAccountRepository::toAccount);
  }

  @Override
  public void updateBalance(Account account) {
    var updated = dsl.update(ACCOUNTS)
        .set(ACCOUNTS.BALANCE, account.balance())
        .where(ACCOUNTS.ID.eq(account.id()))
        .execute();
    if (updated != 1) {
      throw new IllegalStateException("Expected to update account " + account.id() + " but updated " + updated);
    }
  }

  private static Account toAccount(AccountsRecord row) {
    return new Account(
        row.getId(),
        row.getOwnerName(),
        Currency.getInstance(row.getCurrency()),
        row.getBalance(),
        row.getCreatedAt().toInstant());
  }
}
