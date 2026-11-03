package com.sumup.bankaccount.adapters.primary.web;

import com.sumup.bankaccount.adapters.primary.web.model.AccountResponse;
import com.sumup.bankaccount.adapters.primary.web.model.CreateAccountRequest;
import com.sumup.bankaccount.adapters.primary.web.model.TransactionResponse;
import com.sumup.bankaccount.adapters.primary.web.model.TransferRequest;
import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.models.CreateAccountCommand;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransferCommand;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * Converts between the models generated from api.yml and the core's commands and domain objects.
 * The request models are already validated when these methods run.
 */
final class ApiMapper {

  private ApiMapper() {}

  static CreateAccountCommand toCommand(CreateAccountRequest request) {
    var initialBalance = request.getInitialBalance();
    return new CreateAccountCommand(
        request.getOwnerName().strip(),
        Currency.getInstance(request.getCurrency()),
        initialBalance == null ? 0 : initialBalance);
  }

  static TransferCommand toCommand(TransferRequest request, @Nullable String idempotencyKey) {
    return new TransferCommand(
        request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount(), idempotencyKey);
  }

  static AccountResponse toResponse(Account account) {
    return new AccountResponse()
        .id(account.id())
        .ownerName(account.ownerName())
        .currency(account.currency().getCurrencyCode())
        .balance(account.balance())
        .createdAt(account.createdAt());
  }

  static TransactionResponse toResponse(Transaction transaction) {
    return new TransactionResponse()
        .id(transaction.id())
        .type(TransactionResponse.TypeEnum.fromValue(transaction.type().name()))
        .sourceAccountId(transaction.sourceAccountId())
        .targetAccountId(transaction.targetAccountId())
        .amount(transaction.amount())
        .currency(transaction.currency().getCurrencyCode())
        .createdAt(transaction.createdAt());
  }
}
