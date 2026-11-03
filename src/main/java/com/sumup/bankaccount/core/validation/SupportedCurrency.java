package com.sumup.bankaccount.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An uppercase ISO 4217 code of a currency that accounts support, such as {@code EUR}. Which
 * currencies are supported is decided by {@link com.sumup.bankaccount.core.models.Account#supportsCurrency}.
 * {@code null} is valid; use {@code @NotNull} for required values.
 *
 * <p>The API contract (api.yml) puts this on the request's {@code currency} field, so a bad code
 * is a {@code 400} field error before the request reaches a use case.
 */
@Documented
@Constraint(validatedBy = SupportedCurrencyValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedCurrency {

  String message() default
      "must be an uppercase ISO 4217 currency code with a defined number of decimal places, such as EUR";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
