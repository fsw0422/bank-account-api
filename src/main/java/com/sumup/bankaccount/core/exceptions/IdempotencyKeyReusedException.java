package com.sumup.bankaccount.core.exceptions;

/** The key belongs to an earlier request that asked for something different. */
public final class IdempotencyKeyReusedException extends BankingException {

  public IdempotencyKeyReusedException(String idempotencyKey) {
    super("Idempotency-Key '" + idempotencyKey + "' was already used for a different request.");
  }
}
