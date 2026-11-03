package com.sumup.bankaccount.core.exceptions;

import java.util.Currency;

public final class CurrencyMismatchException extends BankingException {

  public CurrencyMismatchException(Currency sourceCurrency, Currency targetCurrency) {
    super("Cannot transfer from a " + sourceCurrency + " account to a " + targetCurrency + " account.");
  }
}
