package com.sumup.bankaccount.core.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sumup.bankaccount.core.exceptions.BalanceLimitExceededException;
import com.sumup.bankaccount.core.exceptions.InsufficientFundsException;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Instant CREATED_AT = Instant.parse("2026-11-02T10:15:30Z");

  @Test
  void depositAddsTheAmount() {
    assertThat(account(100).deposit(50).balance()).isEqualTo(150);
  }

  @Test
  void depositRejectsABalanceAboveTheLargestStorableValue() {
    var nearlyFull = account(Long.MAX_VALUE - 1);

    assertThatThrownBy(() -> nearlyFull.deposit(2)).isInstanceOf(BalanceLimitExceededException.class);
  }

  @Test
  void withdrawSubtractsTheAmount() {
    assertThat(account(100).withdraw(30).balance()).isEqualTo(70);
  }

  @Test
  void withdrawCanEmptyTheAccount() {
    assertThat(account(100).withdraw(100).balance()).isZero();
  }

  @Test
  void withdrawRejectsMoreThanTheBalance() {
    assertThatThrownBy(() -> account(100).withdraw(101)).isInstanceOf(InsufficientFundsException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, Long.MIN_VALUE})
  void depositAndWithdrawRejectNonPositiveAmounts(long amount) {
    var account = account(100);

    assertThatThrownBy(() -> account.deposit(amount)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.withdraw(amount)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void balanceCanNeverBeNegative() {
    assertThatThrownBy(() -> account(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"EUR", "USD", "JPY", "KWD"})
  void supportsCurrenciesWithDecimalPlaces(String code) {
    assertThat(Account.supportsCurrency(Currency.getInstance(code))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"XAU", "XXX", "XTS"})
  void rejectsCurrenciesWithoutDecimalPlaces(String code) {
    var unsupported = Currency.getInstance(code);

    assertThat(Account.supportsCurrency(unsupported)).isFalse();
    // Whatever adapter calls the core, an account in such a currency cannot exist.
    assertThatThrownBy(() -> Account.open("Ada Lovelace", unsupported, 0, CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(code);
  }

  @Test
  void balanceChangesKeepEverythingElse() {
    var original = account(100);

    var changed = original.deposit(1);

    assertThat(changed).usingRecursiveComparison().ignoringFields("balance").isEqualTo(original);
  }

  private static Account account(long balance) {
    return new Account(UUID.randomUUID(), "Ada Lovelace", EUR, balance, CREATED_AT);
  }
}
