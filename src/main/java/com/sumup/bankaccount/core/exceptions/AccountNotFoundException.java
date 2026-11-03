package com.sumup.bankaccount.core.exceptions;

import java.util.UUID;

public final class AccountNotFoundException extends BankingException {

  public AccountNotFoundException(UUID accountId) {
    super("Account " + accountId + " does not exist.");
  }
}
