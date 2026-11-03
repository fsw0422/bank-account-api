package com.sumup.bankaccount.adapters.secondary.postgres;

import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.CREATED_AT;
import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.accountRow;
import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.allTransactions;
import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.givenAccount;
import static com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.givenTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sumup.bankaccount.TestcontainersConfiguration;
import com.sumup.bankaccount.adapters.secondary.postgres.support.CleanDatabaseExtension;
import com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper;
import com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.AccountRow;
import com.sumup.bankaccount.adapters.secondary.postgres.support.RepositoryTestHelper.TransactionRow;
import com.sumup.bankaccount.core.exceptions.IdempotencyKeyConflictException;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransactionType;
import com.sumup.bankaccount.core.ports.secondary.TransactionRepository;
import java.util.Currency;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Runs the transaction (ledger) queries against a real PostgreSQL (Testcontainers). State is set up
 * and checked directly in the table with {@link RepositoryTestHelper}, never through the repository
 * under test.
 */
// Only the persistence adapter: DataSource, Flyway, jOOQ, and the repositories. No web layer, no use cases.
@JooqTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({TestcontainersConfiguration.class, JooqAccountRepository.class, JooqTransactionRepository.class})
// No rollback: writes commit as in production. CleanDatabaseExtension empties the tables first.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(CleanDatabaseExtension.class)
class JooqTransactionRepositoryTest {

  private static final Currency EUR = Currency.getInstance("EUR");

  @Autowired
  private TransactionRepository transactions;
  @Autowired
  private DSLContext dsl;

  private AccountRow source;
  private AccountRow target;

  @BeforeEach
  void givenTwoAccounts() {
    source = accountRow(1_000);
    target = accountRow(0);
    givenAccount(dsl, source);
    givenAccount(dsl, target);
  }

  @Test
  void insertWritesEveryColumnOfATransfer() {
    var transfer = Transaction.transfer(source.id(), target.id(), 300, EUR, "key-1", CREATED_AT);

    transactions.insert(transfer);

    assertThat(allTransactions(dsl)).containsExactly(new TransactionRow(
        transfer.id(), "TRANSFER", source.id(), target.id(), 300, "EUR", "key-1", CREATED_AT));
  }

  @Test
  void insertWritesADepositWithoutSourceOrKey() {
    var deposit = Transaction.deposit(target.id(), 500, EUR, null, CREATED_AT);

    transactions.insert(deposit);

    assertThat(allTransactions(dsl)).containsExactly(new TransactionRow(
        deposit.id(), "DEPOSIT", null, target.id(), 500, "EUR", null, CREATED_AT));
  }

  @Test
  void findByIdempotencyKeyReadsEveryColumnOfTheMatchingTransactionOnly() {
    var wanted = new TransactionRow(
        UUID.randomUUID(), "TRANSFER", source.id(), target.id(), 300, "EUR", "key-1", CREATED_AT);
    givenTransaction(dsl, wanted);
    givenTransaction(dsl, new TransactionRow(
        UUID.randomUUID(), "DEPOSIT", null, target.id(), 5, "EUR", "key-2", CREATED_AT));
    givenTransaction(dsl, new TransactionRow(
        UUID.randomUUID(), "DEPOSIT", null, target.id(), 7, "EUR", null, CREATED_AT));

    assertThat(transactions.findByIdempotencyKey("key-1")).contains(new Transaction(
        wanted.id(), TransactionType.TRANSFER, source.id(), target.id(), 300, EUR, "key-1", CREATED_AT));
  }

  @Test
  void findByIdempotencyKeyFindsNothingForAnUnknownKey() {
    givenTransaction(dsl, new TransactionRow(
        UUID.randomUUID(), "DEPOSIT", null, target.id(), 5, "EUR", "key-2", CREATED_AT));

    assertThat(transactions.findByIdempotencyKey("key-1")).isEmpty();
  }

  @Test
  void rejectsASecondTransactionWithTheSameKey() {
    var first = Transaction.transfer(source.id(), target.id(), 300, EUR, "key-1", CREATED_AT);
    transactions.insert(first);

    assertThatThrownBy(() -> transactions.insert(Transaction.deposit(target.id(), 1, EUR, "key-1", CREATED_AT)))
        .isInstanceOf(IdempotencyKeyConflictException.class);
    assertThat(allTransactions(dsl)).extracting(TransactionRow::id).containsExactly(first.id());
  }

  @Test
  void manyTransactionsMayHaveNoKey() {
    // PostgreSQL treats NULLs as distinct in a UNIQUE column, so keyless requests never conflict.
    for (var amount = 1; amount <= 3; amount++) {
      transactions.insert(Transaction.deposit(target.id(), amount, EUR, null, CREATED_AT));
    }

    assertThat(allTransactions(dsl)).hasSize(3);
  }

  @Test
  void databaseRejectsATransactionForAnUnknownAccount() {
    var unknownAccount = UUID.randomUUID();

    assertThatThrownBy(() -> transactions.insert(Transaction.deposit(unknownAccount, 1, EUR, null, CREATED_AT)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(allTransactions(dsl)).isEmpty();
  }

  @Test
  void databaseRejectsATransferToTheSameAccountEvenIfTheCodeHadABug() {
    var sameAccount = new TransactionRow(
        UUID.randomUUID(), "TRANSFER", source.id(), source.id(), 1, "EUR", null, CREATED_AT);

    assertThatThrownBy(() -> givenTransaction(dsl, sameAccount))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(allTransactions(dsl)).isEmpty();
  }
}
