package com.sumup.bankaccount.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Plain unit test: the constraint is core code and needs no Spring or web layer. */
class SupportedCurrencyValidatorTest {

  private final SupportedCurrencyValidator validator = new SupportedCurrencyValidator();

  @ParameterizedTest
  @ValueSource(strings = {"EUR", "USD", "JPY", "KWD"})
  void acceptsSupportedCodes(String code) {
    assertThat(validator.isValid(code, null)).isTrue();
  }

  @ParameterizedTest
  @NullSource
  void leavesMissingValuesToNotNull(String code) {
    assertThat(validator.isValid(code, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"eur", "Eur", " EUR", "ABC", "EURO", "", "XAU", "XXX"})
  void rejectsUnknownMisspelledOrUnsupportedCodes(String code) {
    assertThat(validator.isValid(code, null)).isFalse();
  }
}
