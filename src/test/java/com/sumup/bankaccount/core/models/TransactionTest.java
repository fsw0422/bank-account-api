package com.sumup.bankaccount.core.models;

import static com.sumup.bankaccount.core.models.TransactionType.DEPOSIT;
import static com.sumup.bankaccount.core.models.TransactionType.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Instant NOW = Instant.parse("2026-11-02T10:15:30Z");
  private static final UUID SOURCE = UUID.randomUUID();
  private static final UUID TARGET = UUID.randomUUID();

  private final Transaction transfer = Transaction.transfer(SOURCE, TARGET, 300, EUR, "key-1", NOW);

  @Test
  void matchesTheSameRequest() {
    assertThat(transfer.isSameRequestAs(TRANSFER, SOURCE, TARGET, 300)).isTrue();
  }

  @Test
  void doesNotMatchADifferentRequest() {
    assertThat(transfer.isSameRequestAs(TRANSFER, SOURCE, TARGET, 301)).isFalse();
    assertThat(transfer.isSameRequestAs(TRANSFER, TARGET, SOURCE, 300)).isFalse();
    assertThat(transfer.isSameRequestAs(DEPOSIT, null, TARGET, 300)).isFalse();
  }

  @Test
  void onlyTransfersHaveASourceAccount() {
    assertThatThrownBy(() -> new Transaction(UUID.randomUUID(), DEPOSIT, SOURCE, TARGET, 1, EUR, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Transaction(UUID.randomUUID(), TRANSFER, null, TARGET, 1, EUR, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void amountMustBePositive() {
    assertThatThrownBy(() -> Transaction.deposit(TARGET, 0, EUR, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
