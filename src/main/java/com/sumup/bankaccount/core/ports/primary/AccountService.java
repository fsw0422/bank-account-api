package com.sumup.bankaccount.core.ports.primary;

import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.models.CreateAccountCommand;
import com.sumup.bankaccount.core.models.DepositCommand;
import com.sumup.bankaccount.core.models.Transaction;
import java.util.UUID;

public interface AccountService {

  Account createAccount(CreateAccountCommand command);

  /** @throws com.sumup.bankaccount.core.exceptions.AccountNotFoundException if it does not exist */
  Account getAccount(UUID accountId);

  /** Adds money to an account. Returns the stored deposit, or the original one for a retried key. */
  Transaction deposit(DepositCommand command);
}
