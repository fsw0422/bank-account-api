package com.sumup.bankaccount.core.ports.secondary;

import com.sumup.bankaccount.core.models.Account;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

  void insert(Account account);

  Optional<Account> findById(UUID accountId);

  /**
   * Finds the account and locks it until the current database transaction ends. Other callers
   * that lock the same account wait until then.
   */
  Optional<Account> findByIdForUpdate(UUID accountId);

  void updateBalance(Account account);
}
