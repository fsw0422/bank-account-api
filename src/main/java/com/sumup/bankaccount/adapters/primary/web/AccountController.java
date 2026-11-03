package com.sumup.bankaccount.adapters.primary.web;

import com.sumup.bankaccount.adapters.primary.web.model.AccountResponse;
import com.sumup.bankaccount.adapters.primary.web.model.CreateAccountRequest;
import com.sumup.bankaccount.adapters.primary.web.model.DepositRequest;
import com.sumup.bankaccount.adapters.primary.web.model.TransactionResponse;
import com.sumup.bankaccount.core.models.DepositCommand;
import com.sumup.bankaccount.core.ports.primary.AccountService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Routes, validation, and parameter binding come from {@link AccountsApi}, generated from api.yml. */
@RestController
class AccountController implements AccountsApi {

  private final AccountService accountService;

  AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @Override
  public ResponseEntity<AccountResponse> createAccount(CreateAccountRequest request) {
    var account = accountService.createAccount(ApiMapper.toCommand(request));
    var location = ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/accounts/{accountId}")
        .buildAndExpand(account.id())
        .toUri();
    return ResponseEntity.created(location).body(ApiMapper.toResponse(account));
  }

  @Override
  public ResponseEntity<AccountResponse> getAccount(UUID accountId) {
    return ResponseEntity.ok(ApiMapper.toResponse(accountService.getAccount(accountId)));
  }

  @Override
  public ResponseEntity<TransactionResponse> deposit(
      UUID accountId, DepositRequest request, @Nullable String idempotencyKey) {
    var deposit = accountService.deposit(new DepositCommand(accountId, request.getAmount(), idempotencyKey));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiMapper.toResponse(deposit));
  }
}
