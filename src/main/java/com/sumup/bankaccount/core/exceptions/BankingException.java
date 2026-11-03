package com.sumup.bankaccount.core.exceptions;

/**
 * A request that the business rules reject. Sealed, so the web layer's mapping to HTTP statuses
 * is checked by the compiler: adding a new rule without mapping it does not compile.
 */
public abstract sealed class BankingException extends RuntimeException
    permits AccountNotFoundException,
        BalanceLimitExceededException,
        CurrencyMismatchException,
        IdempotencyKeyConflictException,
        IdempotencyKeyReusedException,
        InsufficientFundsException,
        SameAccountTransferException {

  protected BankingException(String message) {
    super(message);
  }
}
