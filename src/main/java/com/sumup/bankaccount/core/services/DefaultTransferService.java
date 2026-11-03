package com.sumup.bankaccount.core.services;

import com.sumup.bankaccount.core.exceptions.AccountNotFoundException;
import com.sumup.bankaccount.core.exceptions.CurrencyMismatchException;
import com.sumup.bankaccount.core.exceptions.SameAccountTransferException;
import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransactionType;
import com.sumup.bankaccount.core.models.TransferCommand;
import com.sumup.bankaccount.core.ports.primary.TransferService;
import com.sumup.bankaccount.core.ports.secondary.AccountRepository;
import com.sumup.bankaccount.core.ports.secondary.TransactionRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultTransferService implements TransferService {

  private static final Logger log = LoggerFactory.getLogger(DefaultTransferService.class);

  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final IdempotencyGuard idempotencyGuard;
  private final Clock clock;

  DefaultTransferService(
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

  /**
   * One database transaction: lock both accounts, check the rules, update both balances, and store
   * the ledger entry. Any exception rolls everything back.
   */
  @Override
  @Transactional
  public Transaction transfer(TransferCommand command) {
    var sourceId = command.sourceAccountId();
    var targetId = command.targetAccountId();
    var amount = command.amount();
    if (sourceId.equals(targetId)) {
      throw new SameAccountTransferException(sourceId);
    }

    var locked = lockInFixedOrder(sourceId, targetId);
    var source = locked.get(sourceId);
    var target = locked.get(targetId);

    var earlier = idempotencyGuard.findEarlierResult(
        command.idempotencyKey(), TransactionType.TRANSFER, sourceId, targetId, amount);
    if (earlier.isPresent()) {
      log.info("Returned earlier transfer {} for a retried request", earlier.get().id());
      return earlier.get();
    }

    if (!source.currency().equals(target.currency())) {
      throw new CurrencyMismatchException(source.currency(), target.currency());
    }
    // Both checks run before any write: insufficient funds first, then the target's upper limit.
    var debitedSource = source.withdraw(amount);
    var creditedTarget = target.deposit(amount);
    accounts.updateBalance(debitedSource);
    accounts.updateBalance(creditedTarget);

    var transfer = Transaction.transfer(
        sourceId, targetId, amount, source.currency(), command.idempotencyKey(),
        clock.instant().truncatedTo(ChronoUnit.MICROS));
    transactions.insert(transfer);
    log.info("Transfer {} of {} minor units from {} to {}", transfer.id(), amount, sourceId, targetId);
    return transfer;
  }

  /**
   * Locks both accounts, always the smaller ID first. Without a fixed order, a transfer A to B and a
   * transfer B to A could each hold one lock and wait forever for the other (a deadlock).
   */
  private Map<UUID, Account> lockInFixedOrder(UUID oneId, UUID otherId) {
    var firstId = oneId.compareTo(otherId) < 0 ? oneId : otherId;
    var secondId = firstId.equals(oneId) ? otherId : oneId;
    var first = lock(firstId);
    var second = lock(secondId);
    return Map.of(firstId, first, secondId, second);
  }

  private Account lock(UUID accountId) {
    return accounts.findByIdForUpdate(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));
  }
}
