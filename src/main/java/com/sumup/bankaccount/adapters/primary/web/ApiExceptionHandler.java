package com.sumup.bankaccount.adapters.primary.web;

import com.sumup.bankaccount.core.exceptions.AccountNotFoundException;
import com.sumup.bankaccount.core.exceptions.BalanceLimitExceededException;
import com.sumup.bankaccount.core.exceptions.BankingException;
import com.sumup.bankaccount.core.exceptions.CurrencyMismatchException;
import com.sumup.bankaccount.core.exceptions.IdempotencyKeyConflictException;
import com.sumup.bankaccount.core.exceptions.IdempotencyKeyReusedException;
import com.sumup.bankaccount.core.exceptions.InsufficientFundsException;
import com.sumup.bankaccount.core.exceptions.SameAccountTransferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 problem response with a machine-readable {@code code}.
 * The extended Spring class already covers its own errors (bad JSON, wrong content type, unknown
 * paths); this class adds the business errors, field details, and a safe fallback.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final String CODE = "code";

  private record ApiError(HttpStatus status, String code, String title) {}

  /** One field or header that failed validation. */
  record InvalidField(String field, @Nullable String message) {}

  @ExceptionHandler(BankingException.class)
  ResponseEntity<Object> handleBankingException(BankingException exception, WebRequest request) {
    // Exhaustive over the sealed class: a new business error without a mapping does not compile.
    var error = switch (exception) {
      case AccountNotFoundException e ->
          new ApiError(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found");
      case SameAccountTransferException e ->
          new ApiError(HttpStatus.UNPROCESSABLE_CONTENT, "SAME_ACCOUNT_TRANSFER", "Same source and target account");
      case CurrencyMismatchException e ->
          new ApiError(HttpStatus.UNPROCESSABLE_CONTENT, "CURRENCY_MISMATCH", "Currencies do not match");
      case InsufficientFundsException e ->
          new ApiError(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS", "Insufficient funds");
      case BalanceLimitExceededException e ->
          new ApiError(HttpStatus.UNPROCESSABLE_CONTENT, "BALANCE_LIMIT_EXCEEDED", "Balance limit exceeded");
      case IdempotencyKeyReusedException e ->
          new ApiError(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key reused");
      case IdempotencyKeyConflictException e ->
          new ApiError(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "Concurrent request with the same key");
    };
    return problem(error, exception.getMessage(), new HttpHeaders(), request);
  }

  /**
   * The database is down or busy: tell the client to retry instead of reporting a bug. A real
   * outage rarely surfaces as a data access error. Spring fails to open the database transaction
   * ({@link CannotCreateTransactionException}), or a pooled connection has died and the rollback
   * fails ({@link TransactionSystemException}), which hides the original error.
   */
  @ExceptionHandler({
      TransientDataAccessException.class,
      DataAccessResourceFailureException.class,
      CannotCreateTransactionException.class,
      TransactionSystemException.class
  })
  ResponseEntity<Object> handleDatabaseUnavailable(Exception exception, WebRequest request) {
    log.warn("Database unavailable or busy", exception);
    var headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, "1");
    var error = new ApiError(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Service unavailable");
    return problem(error, "The service is busy. Please retry shortly.", headers, request);
  }

  /** Anything unexpected: log it in full, but return no internal details. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
    log.error("Unexpected error", exception);
    var error = new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error");
    return problem(error, "Something went wrong. Please try again later.", new HttpHeaders(), request);
  }

  /** Invalid fields in a request body. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    var errors = exception.getBindingResult().getFieldErrors().stream()
        .map(ApiExceptionHandler::toInvalidField)
        .toList();
    return invalidRequest(exception.getBody(), errors, exception, headers, status, request);
  }

  /** Invalid headers or path values, together with any invalid body fields of the same request. */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    var errors = new ArrayList<InvalidField>();
    for (var result : exception.getParameterValidationResults()) {
      if (result instanceof ParameterErrors bodyErrors) {
        bodyErrors.getFieldErrors().forEach(error -> errors.add(toInvalidField(error)));
      } else {
        var name = parameterName(result.getMethodParameter());
        result.getResolvableErrors().forEach(error -> errors.add(new InvalidField(name, error.getDefaultMessage())));
      }
    }
    return invalidRequest(exception.getBody(), errors, exception, headers, status, request);
  }

  /** Adds a {@code code} to the problems Spring MVC creates itself, such as 400, 404, or 415. */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem && !hasCode(problem)) {
      problem.setProperty(CODE, statusCode.value() == 400 ? "INVALID_REQUEST" : codeFor(statusCode));
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private ResponseEntity<Object> invalidRequest(
      ProblemDetail problem,
      List<InvalidField> errors,
      Exception exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request
  ) {
    problem.setDetail("The request has invalid fields.");
    problem.setProperty("errors", errors);
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  private ResponseEntity<Object> problem(ApiError error, String detail, HttpHeaders headers, WebRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(error.status(), detail);
    problem.setTitle(error.title());
    problem.setProperty(CODE, error.code());
    return createResponseEntity(problem, headers, error.status(), request);
  }

  private static InvalidField toInvalidField(FieldError error) {
    return new InvalidField(error.getField(), error.getDefaultMessage());
  }

  private static String parameterName(MethodParameter parameter) {
    var header = parameter.getParameterAnnotation(RequestHeader.class);
    if (header != null && !header.name().isEmpty()) {
      return header.name();
    }
    return Objects.requireNonNullElse(parameter.getParameterName(), "parameter");
  }

  private static boolean hasCode(ProblemDetail problem) {
    var properties = problem.getProperties();
    return properties != null && properties.containsKey(CODE);
  }

  private static String codeFor(HttpStatusCode statusCode) {
    var status = HttpStatus.resolve(statusCode.value());
    return status == null ? "HTTP_" + statusCode.value() : status.name();
  }
}
