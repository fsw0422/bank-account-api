package com.sumup.bankaccount.core.exceptions;

import java.util.UUID;

public final class BalanceLimitExceededException extends BankingException {

  public BalanceLimitExceededException(UUID accountId) {
    super("The balance of account " + accountId + " would exceed the largest supported value.");
  }
}
