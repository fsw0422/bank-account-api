package com.sumup.bankaccount.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sumup.bankaccount.core.exceptions.AccountNotFoundException;
import com.sumup.bankaccount.core.exceptions.BalanceLimitExceededException;
import com.sumup.bankaccount.core.exceptions.CurrencyMismatchException;
import com.sumup.bankaccount.core.exceptions.IdempotencyKeyReusedException;
import com.sumup.bankaccount.core.exceptions.InsufficientFundsException;
import com.sumup.bankaccount.core.exceptions.SameAccountTransferException;
import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransactionType;
import com.sumup.bankaccount.core.models.TransferCommand;
import com.sumup.bankaccount.core.ports.secondary.AccountRepository;
import com.sumup.bankaccount.core.ports.secondary.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTransferServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:15:30.123456789Z");
  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Currency USD = Currency.getInstance("USD");
  /** LOW_ID sorts before HIGH_ID, so the tests can check the lock order. */
  private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Mock
  private AccountRepository accounts;
  @Mock
  private TransactionRepository transactions;

  private DefaultTransferService service;

  @BeforeEach
  void setUp() {
    service = new DefaultTransferService(
        accounts, transactions, new IdempotencyGuard(transactions), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void movesMoneyAndRecordsTheTransfer() {
    givenAccounts(account(LOW_ID, 1_000, EUR), account(HIGH_ID, 50, EUR));

    var transfer = service.transfer(new TransferCommand(LOW_ID, HIGH_ID, 300, "key-1"));

    verify(accounts).updateBalance(account(LOW_ID, 700, EUR));
    verify(accounts).updateBalance(account(HIGH_ID, 350, EUR));
    verify(transactions).insert(transfer);
    assertThat(transfer.type()).isEqualTo(TransactionType.TRANSFER);
    assertThat(transfer.sourceAccountId()).isEqualTo(LOW_ID);
    assertThat(transfer.targetAccountId()).isEqualTo(HIGH_ID);
    assertThat(transfer.amount()).isEqualTo(300);
    assertThat(transfer.currency()).isEqualTo(EUR);
    assertThat(transfer.idempotencyKey()).isEqualTo("key-1");
    // Truncated to microseconds, the precision PostgreSQL stores.
    assertThat(transfer.createdAt()).isEqualTo(Instant.parse("2026-11-02T10:15:30.123456Z"));
  }

  static Stream<Arguments> bothDirections() {
    return Stream.of(Arguments.of(LOW_ID, HIGH_ID), Arguments.of(HIGH_ID, LOW_ID));
  }

  @ParameterizedTest(name = "transfer {0} to {1}")
  @MethodSource("bothDirections")
  void locksTheSmallerIdFirstInBothDirections(UUID sourceId, UUID targetId) {
    givenAccounts(account(LOW_ID, 100, EUR), account(HIGH_ID, 100, EUR));

    service.transfer(new TransferCommand(sourceId, targetId, 10, null));

    var order = inOrder(accounts);
    order.verify(accounts).findByIdForUpdate(LOW_ID);
    order.verify(accounts).findByIdForUpdate(HIGH_ID);
  }

  @Test
  void rejectsATransferToTheSameAccountWithoutTouchingStorage() {
    assertThatThrownBy(() -> service.transfer(new TransferCommand(LOW_ID, LOW_ID, 10, null)))
        .isInstanceOf(SameAccountTransferException.class);

    verifyNoInteractions(accounts, transactions);
  }

  @Test
  void rejectsAnUnknownAccount() {
    givenAccounts(account(LOW_ID, 100, EUR));

    assertThatThrownBy(() -> service.transfer(new TransferCommand(LOW_ID, HIGH_ID, 10, null)))
        .isInstanceOf(AccountNotFoundException.class)
        .hasMessageContaining(HIGH_ID.toString());

    verify(accounts, never()).updateBalance(any());
    verifyNoInteractions(transactions);
  }

  @Test
  void rejectsDifferentCurrencies() {
    givenAccounts(account(LOW_ID, 100, EUR), account(HIGH_ID, 100, USD));

    assertThatThrownBy(() -> service.transfer(new TransferCommand(LOW_ID, HIGH_ID, 10, null)))
        .isInstanceOf(CurrencyMismatchException.class);

    verify(accounts, never()).updateBalance(any());
    verifyNoInteractions(transactions);
  }

  @Test
  void rejectsMoreThanTheSourceBalance() {
    givenAccounts(account(LOW_ID, 100, EUR), account(HIGH_ID, 0, EUR));

    assertThatThrownBy(() -> service.transfer(new TransferCommand(LOW_ID, HIGH_ID, 101, null)))
        .isInstanceOf(InsufficientFundsException.class);

    verify(accounts, never()).updateBalance(any());
    verifyNoInteractions(transactions);
  }

  @Test
  void rejectsATargetBalanceAboveTheLimitBeforeChangingTheSource() {
    givenAccounts(account(LOW_ID, 100, EUR), account(HIGH_ID, Long.MAX_VALUE - 5, EUR));

    assertThatThrownBy(() -> service.transfer(new TransferCommand(LOW_ID, HIGH_ID, 10, null)))
        .isInstanceOf(BalanceLimitExceededException.class);

    verify(accounts, never()).updateBalance(any());
  }

  @Test
  void returnsTheEarlierTransferForARetriedKey() {
    // The source is empty now, but the retry must still return the original success.
    givenAccounts(account(LOW_ID, 0, EUR), account(HIGH_ID, 300, EUR));
    var earlier = Transaction.transfer(LOW_ID, HIGH_ID, 300, EUR, "key-1", CREATED_AT);
    when(transactions.findByIdempotencyKey("key-1")).thenReturn(Optional.of(earlier));

    var result = service.transfer(new TransferCommand(LOW_ID, HIGH_ID, 300, "key-1"));

    assertThat(result).isEqualTo(earlier);
    verify(accounts, never()).updateBalance(any());
    verify(transactions, never()).insert(any());
  }

  @Test
  void rejectsAKeyReusedForADifferentAmount() {
    givenAccounts(account(LOW_ID, 1_000, EUR), account(HIGH_ID, 0, EUR));
    var earlier = Transaction.transfer(LOW_ID, HIGH_ID, 300, EUR, "key-1", CREATED_AT);
    when(transactions.findByIdempotencyKey("key-1")).thenReturn(Optional.of(earlier));

    assertThatThrownBy(() -> service.transfer(new TransferCommand(LOW_ID, HIGH_ID, 999, "key-1")))
        .isInstanceOf(IdempotencyKeyReusedException.class);

    verify(accounts, never()).updateBalance(any());
    verify(transactions, never()).insert(any());
  }

  private void givenAccounts(Account... existing) {
    for (var account : existing) {
      when(accounts.findByIdForUpdate(account.id())).thenReturn(Optional.of(account));
    }
  }

  private static Account account(UUID id, long balance, Currency currency) {
    return new Account(id, "Owner " + id, currency, balance, CREATED_AT);
  }
}
