package com.sumup.bankaccount.core.services;

import com.sumup.bankaccount.core.exceptions.AccountNotFoundException;
import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.models.CreateAccountCommand;
import com.sumup.bankaccount.core.models.DepositCommand;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransactionType;
import com.sumup.bankaccount.core.ports.primary.AccountService;
import com.sumup.bankaccount.core.ports.secondary.AccountRepository;
import com.sumup.bankaccount.core.ports.secondary.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultAccountService implements AccountService {

  private static final Logger log = LoggerFactory.getLogger(DefaultAccountService.class);

  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final IdempotencyGuard idempotencyGuard;
  private final Clock clock;

  DefaultAccountService(
      AccountRepository accounts,
      TransactionRepository transactions,
      IdempotencyGuard idempotencyGuard,
      Clock clock
  ) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.idempotencyGuard = idempotencyGuard;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Account createAccount(CreateAccountCommand command) {
    var now = now();
    var account = Account.open(command.ownerName(), command.currency(), command.initialBalance(), now);
    accounts.insert(account);
    // Record the opening balance as a deposit, so the ledger explains every balance.
    if (command.initialBalance() > 0) {
      transactions.insert(
          Transaction.deposit(account.id(), command.initialBalance(), account.currency(), null, now));
    }
    log.info("Created account {} in {}", account.id(), account.currency());
    return account;
  }

  @Override
  @Transactional(readOnly = true)
  public Account getAccount(UUID accountId) {
    return accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
  }

  @Override
  @Transactional
  public Transaction deposit(DepositCommand command) {
    var accountId = command.accountId();
    // Lock before the idempotency check, so a concurrent retry waits here for the first request.
    var account = accounts.findByIdForUpdate(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));

    var earlier = idempotencyGuard.findEarlierResult(
        command.idempotencyKey(), TransactionType.DEPOSIT, null, accountId, command.amount());
    if (earlier.isPresent()) {
      log.info("Returned earlier deposit {} for a retried request", earlier.get().id());
      return earlier.get();
    }

    accounts.updateBalance(account.deposit(command.amount()));
    var deposit = Transaction.deposit(
        accountId, command.amount(), account.currency(), command.idempotencyKey(), now());
    transactions.insert(deposit);
    log.info("Deposit {} of {} minor units into account {}", deposit.id(), deposit.amount(), accountId);
    return deposit;
  }

  /** PostgreSQL stores microseconds, so drop the rest to return what a later read returns. */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
