package com.sumup.bankaccount.core.validation;

import com.sumup.bankaccount.core.models.Account;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/** Accepts a text only if it is an exact ISO 4217 code that {@link Account#supportsCurrency} allows. */
class SupportedCurrencyValidator implements ConstraintValidator<SupportedCurrency, String> {

  private static final Set<String> SUPPORTED_CODES = Currency.getAvailableCurrencies().stream()
      .filter(Account::supportsCurrency)
      .map(Currency::getCurrencyCode)
      .collect(Collectors.toUnmodifiableSet());

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || SUPPORTED_CODES.contains(value);
  }
}
