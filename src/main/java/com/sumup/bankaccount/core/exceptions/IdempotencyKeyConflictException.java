package com.sumup.bankaccount.core.exceptions;

/** Another request with the same key was stored first while this one was running. */
public final class IdempotencyKeyConflictException extends BankingException {

  public IdempotencyKeyConflictException(String idempotencyKey) {
    super("Another request with Idempotency-Key '" + idempotencyKey + "' finished first. Retry to get its result.");
  }
}
