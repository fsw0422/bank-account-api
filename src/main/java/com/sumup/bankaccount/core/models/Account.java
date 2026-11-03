package com.sumup.bankaccount.core.models;

import com.sumup.bankaccount.core.exceptions.BalanceLimitExceededException;
import com.sumup.bankaccount.core.exceptions.InsufficientFundsException;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * A bank account. Immutable: balance changes return a new instance, so the rules live here and
 * callers decide when to persist.
 *
 * @param balance in minor units of {@code currency}, never negative
 */
public record Account(UUID id, String ownerName, Currency currency, long balance, Instant createdAt) {

  public Account {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(ownerName, "ownerName");
    Objects.requireNonNull(currency, "currency");
    Objects.requireNonNull(createdAt, "createdAt");
    if (!supportsCurrency(currency)) {
      // The web layer already rejects this, so reaching here is a programming error.
      throw new IllegalArgumentException("currency " + currency + " has no defined number of decimal places");
    }
    if (balance < 0) {
      throw new IllegalArgumentException("balance must not be negative");
    }
  }

  /**
   * Accounts only use currencies with a defined number of decimal places, such as EUR (2) or
   * JPY (0), because amounts are whole minor units. Codes like XAU (gold) or XXX have none.
   * Adapters use this to reject unsupported currencies before they reach the core.
   */
  public static boolean supportsCurrency(Currency currency) {
    return currency.getDefaultFractionDigits() >= 0;
  }

  public static Account open(String ownerName, Currency currency, long initialBalance, Instant now) {
    return new Account(UUID.randomUUID(), ownerName, currency, initialBalance, now);
  }

  public Account deposit(long amount) {
    requirePositive(amount);
    try {
      return withBalance(Math.addExact(balance, amount));
    } catch (ArithmeticException overflow) {
      throw new BalanceLimitExceededException(id);
    }
  }

  public Account withdraw(long amount) {
    requirePositive(amount);
    if (amount > balance) {
      throw new InsufficientFundsException(id);
    }
    return withBalance(balance - amount);
  }

  private Account withBalance(long newBalance) {
    return new Account(id, ownerName, currency, newBalance, createdAt);
  }

  private static void requirePositive(long amount) {
    // The web layer already rejects this, so reaching here is a programming error.
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
  }
}
