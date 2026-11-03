package com.sumup.bankaccount.core.exceptions;

import java.util.UUID;

public final class SameAccountTransferException extends BankingException {

  public SameAccountTransferException(UUID accountId) {
    super("Cannot transfer from account " + accountId + " to itself.");
  }
}
