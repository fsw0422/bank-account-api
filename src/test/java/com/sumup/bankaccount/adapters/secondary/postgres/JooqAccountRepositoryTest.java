package com.sumup.bankaccount.adapters.secondary.postgres;

import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.CREATED_AT;
import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.accountRow;
import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.allAccounts;
import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.givenAccount;
import static com.sumup.bankaccount.adapters.secondary.postgres.jooq.Tables.ACCOUNTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sumup.bankaccount.TestcontainersConfiguration;
import com.sumup.bankaccount.adapters.secondary.postgres.support.CleanDatabaseExtension;
import com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper;
import com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.AccountRow;
import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.ports.secondary.AccountRepository;
import java.sql.SQLException;
import java.util.Currency;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the account queries against a real PostgreSQL (Testcontainers). State is set up and checked
 * directly in the table with {@link RepositoryTestHelper}, never through the repository under test.
 */
// Only the persistence adapter: DataSource, Flyway, jOOQ, and the repositories. No web layer, no use cases.
@JooqTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({TestcontainersConfiguration.class, JooqAccountRepository.class, JooqTransactionRepository.class})
// No rollback: writes commit as in production. CleanDatabaseExtension empties the tables first.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(CleanDatabaseExtension.class)
class JooqAccountRepositoryTest {

  /** PostgreSQL error code for "could not obtain lock", raised by FOR UPDATE NOWAIT. */
  private static final String LOCK_NOT_AVAILABLE = "55P03";
  private static final Currency JPY = Currency.getInstance("JPY");

  @Autowired
  private AccountRepository accounts;
  @Autowired
  private DSLContext dsl;
  @Autowired
  private DataSource dataSource;
  @Autowired
  private TransactionTemplate transactionTemplate;

  @Test
  void insertWritesEveryColumn() {
    var account = new Account(UUID.randomUUID(), "Ada Lovelace", JPY, 1_234, CREATED_AT);

    accounts.insert(account);

    assertThat(allAccounts(dsl))
        .containsExactly(new AccountRow(account.id(), "Ada Lovelace", "JPY", 1_234, CREATED_AT));
  }

  @Test
  void findByIdReadsEveryColumnOfThatAccountOnly() {
    var wanted = new AccountRow(UUID.randomUUID(), "Ada Lovelace", "JPY", 1_234, CREATED_AT);
    givenAccount(dsl, wanted);
    givenAccount(dsl, accountRow(999));

    assertThat(accounts.findById(wanted.id()))
        .contains(new Account(wanted.id(), "Ada Lovelace", JPY, 1_234, CREATED_AT));
  }

  @Test
  void findByIdForUpdateReadsEveryColumnOfThatAccountOnly() {
    var wanted = new AccountRow(UUID.randomUUID(), "Ada Lovelace", "JPY", 1_234, CREATED_AT);
    givenAccount(dsl, wanted);
    givenAccount(dsl, accountRow(999));

    assertThat(accounts.findByIdForUpdate(wanted.id()))
        .contains(new Account(wanted.id(), "Ada Lovelace", JPY, 1_234, CREATED_AT));
  }

  @Test
  void findsNothingForAnUnknownId() {
    givenAccount(dsl, accountRow(100));

    assertThat(accounts.findById(UUID.randomUUID())).isEmpty();
    assertThat(accounts.findByIdForUpdate(UUID.randomUUID())).isEmpty();
  }

  @Test
  void findByIdForUpdateLocksThatRowUntilTheDatabaseTransactionEnds() {
    var locked = accountRow(100);
    var other = accountRow(200);
    givenAccount(dsl, locked);
    givenAccount(dsl, other);

    transactionTemplate.executeWithoutResult(status -> {
      accounts.findByIdForUpdate(locked.id());

      assertThat(canLockFromAnotherConnection(locked.id())).as("the locked row").isFalse();
      assertThat(canLockFromAnotherConnection(other.id())).as("any other row").isTrue();
    });

    assertThat(canLockFromAnotherConnection(locked.id())).as("the row after commit").isTrue();
  }

  @Test
  void updateBalanceChangesOnlyThatAccount() {
    var target = accountRow(100);
    var other = accountRow(500);
    givenAccount(dsl, target);
    givenAccount(dsl, other);

    accounts.updateBalance(new Account(target.id(), "Owner", Currency.getInstance("EUR"), 150, CREATED_AT));

    assertThat(allAccounts(dsl)).containsExactlyInAnyOrder(target.withBalance(150), other);
  }

  @Test
  void updateBalanceFailsLoudlyForAnUnknownAccount() {
    var existing = accountRow(100);
    givenAccount(dsl, existing);

    assertThatThrownBy(() -> accounts.updateBalance(
        new Account(UUID.randomUUID(), "Owner", Currency.getInstance("EUR"), 150, CREATED_AT)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(allAccounts(dsl)).containsExactly(existing);
  }

  @Test
  void databaseRejectsANegativeBalanceEvenIfTheCodeHadABug() {
    var row = accountRow(100);
    givenAccount(dsl, row);

    assertThatThrownBy(() -> dsl.update(ACCOUNTS)
        .set(ACCOUNTS.BALANCE, -1L)
        .where(ACCOUNTS.ID.eq(row.id()))
        .execute())
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(allAccounts(dsl)).containsExactly(row);
  }

  /**
   * Tries to lock the row from a second database connection, without waiting. Returns false if
   * another database transaction holds the lock.
   */
  private boolean canLockFromAnotherConnection(UUID accountId) {
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        DSL.using(connection, SQLDialect.POSTGRES)
            .selectFrom(ACCOUNTS)
            .where(ACCOUNTS.ID.eq(accountId))
            .forUpdate()
            .noWait()
            .fetch();
        return true;
      } catch (org.jooq.exception.DataAccessException failed) {
        if (LOCK_NOT_AVAILABLE.equals(failed.sqlState())) {
          return false;
        }
        throw failed;
      } finally {
        connection.rollback();
      }
    } catch (SQLException connectionFailed) {
      throw new IllegalStateException(connectionFailed);
    }
  }
}
