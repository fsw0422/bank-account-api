package com.sumup.bankaccount.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sumup.bankaccount.core.exceptions.AccountNotFoundException;
import com.sumup.bankaccount.core.exceptions.BalanceLimitExceededException;
import com.sumup.bankaccount.core.exceptions.IdempotencyKeyReusedException;
import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.models.CreateAccountCommand;
import com.sumup.bankaccount.core.models.DepositCommand;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransactionType;
import com.sumup.bankaccount.core.ports.secondary.AccountRepository;
import com.sumup.bankaccount.core.ports.secondary.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultAccountServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:15:30.123456789Z");
  private static final Instant NOW_IN_MICROS = Instant.parse("2026-11-02T10:15:30.123456Z");
  private static final Currency EUR = Currency.getInstance("EUR");
  private static final UUID ACCOUNT_ID = UUID.randomUUID();

  @Mock
  private AccountRepository accounts;
  @Mock
  private TransactionRepository transactions;
  @Captor
  private ArgumentCaptor<Transaction> transactionCaptor;

  private DefaultAccountService service;

  @BeforeEach
  void setUp() {
    service = new DefaultAccountService(
        accounts, transactions, new IdempotencyGuard(transactions), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createAccountStoresTheAccountAndAnOpeningDeposit() {
    var account = service.createAccount(new CreateAccountCommand("Ada Lovelace", EUR, 10_000));

    assertThat(account.ownerName()).isEqualTo("Ada Lovelace");
    assertThat(account.currency()).isEqualTo(EUR);
    assertThat(account.balance()).isEqualTo(10_000);
    assertThat(account.createdAt()).isEqualTo(NOW_IN_MICROS);
    verify(accounts).insert(account);

    verify(transactions).insert(transactionCaptor.capture());
    var openingDeposit = transactionCaptor.getValue();
    assertThat(openingDeposit.type()).isEqualTo(TransactionType.DEPOSIT);
    assertThat(openingDeposit.targetAccountId()).isEqualTo(account.id());
    assertThat(openingDeposit.amount()).isEqualTo(10_000);
    assertThat(openingDeposit.idempotencyKey()).isNull();
  }

  @Test
  void createAccountWithZeroBalanceStoresNoDeposit() {
    var account = service.createAccount(new CreateAccountCommand("Ada Lovelace", EUR, 0));

    assertThat(account.balance()).isZero();
    verifyNoInteractions(transactions);
  }

  @Test
  void getAccountReturnsTheStoredAccount() {
    var stored = account(500);
    when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(stored));

    assertThat(service.getAccount(ACCOUNT_ID)).isEqualTo(stored);
  }

  @Test
  void getAccountRejectsAnUnknownId() {
    assertThatThrownBy(() -> service.getAccount(ACCOUNT_ID))
        .isInstanceOf(AccountNotFoundException.class)
        .hasMessageContaining(ACCOUNT_ID.toString());
  }

  @Test
  void depositIncreasesTheBalanceAndRecordsTheDeposit() {
    when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account(500)));

    var deposit = service.deposit(new DepositCommand(ACCOUNT_ID, 250, "key-1"));

    verify(accounts).updateBalance(account(750));
    verify(transactions).insert(deposit);
    assertThat(deposit.type()).isEqualTo(TransactionType.DEPOSIT);
    assertThat(deposit.sourceAccountId()).isNull();
    assertThat(deposit.targetAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(deposit.amount()).isEqualTo(250);
    assertThat(deposit.idempotencyKey()).isEqualTo("key-1");
    assertThat(deposit.createdAt()).isEqualTo(NOW_IN_MICROS);
  }

  @Test
  void depositRejectsAnUnknownAccount() {
    assertThatThrownBy(() -> service.deposit(new DepositCommand(ACCOUNT_ID, 250, null)))
        .isInstanceOf(AccountNotFoundException.class);

    verify(accounts, never()).updateBalance(any());
    verifyNoInteractions(transactions);
  }

  @Test
  void depositRejectsABalanceAboveTheLimit() {
    when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account(Long.MAX_VALUE)));

    assertThatThrownBy(() -> service.deposit(new DepositCommand(ACCOUNT_ID, 1, null)))
        .isInstanceOf(BalanceLimitExceededException.class);

    verify(accounts, never()).updateBalance(any());
    verifyNoInteractions(transactions);
  }

  @Test
  void depositReturnsTheEarlierDepositForARetriedKey() {
    when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account(750)));
    var earlier = Transaction.deposit(ACCOUNT_ID, 250, EUR, "key-1", NOW_IN_MICROS);
    when(transactions.findByIdempotencyKey("key-1")).thenReturn(Optional.of(earlier));

    var result = service.deposit(new DepositCommand(ACCOUNT_ID, 250, "key-1"));

    assertThat(result).isEqualTo(earlier);
    verify(accounts, never()).updateBalance(any());
    verify(transactions, never()).insert(any());
  }

  @Test
  void depositRejectsAKeyReusedForADifferentAmount() {
    when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account(750)));
    var earlier = Transaction.deposit(ACCOUNT_ID, 250, EUR, "key-1", NOW_IN_MICROS);
    when(transactions.findByIdempotencyKey("key-1")).thenReturn(Optional.of(earlier));

    assertThatThrownBy(() -> service.deposit(new DepositCommand(ACCOUNT_ID, 999, "key-1")))
        .isInstanceOf(IdempotencyKeyReusedException.class);

    verify(accounts, never()).updateBalance(any());
  }

  private static Account account(long balance) {
    return new Account(ACCOUNT_ID, "Ada Lovelace", EUR, balance, Instant.parse("2026-01-01T00:00:00Z"));
  }
}
