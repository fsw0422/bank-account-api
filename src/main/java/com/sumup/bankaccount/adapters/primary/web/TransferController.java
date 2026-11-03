package com.sumup.bankaccount.adapters.primary.web;

import com.sumup.bankaccount.adapters.primary.web.model.TransactionResponse;
import com.sumup.bankaccount.adapters.primary.web.model.TransferRequest;
import com.sumup.bankaccount.core.ports.primary.TransferService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Routes, validation, and parameter binding come from {@link TransfersApi}, generated from api.yml. */
@RestController
class TransferController implements TransfersApi {

  private final TransferService transferService;

  TransferController(TransferService transferService) {
    this.transferService = transferService;
  }

  @Override
  public ResponseEntity<TransactionResponse> transfer(TransferRequest request, @Nullable String idempotencyKey) {
    var transfer = transferService.transfer(ApiMapper.toCommand(request, idempotencyKey));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiMapper.toResponse(transfer));
  }
}
