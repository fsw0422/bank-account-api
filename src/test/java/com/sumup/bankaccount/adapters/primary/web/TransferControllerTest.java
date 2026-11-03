package com.sumup.bankaccount.adapters.primary.web;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sumup.bankaccount.core.exceptions.AccountNotFoundException;
import com.sumup.bankaccount.core.exceptions.BalanceLimitExceededException;
import com.sumup.bankaccount.core.exceptions.BankingException;
import com.sumup.bankaccount.core.exceptions.CurrencyMismatchException;
import com.sumup.bankaccount.core.exceptions.IdempotencyKeyConflictException;
import com.sumup.bankaccount.core.exceptions.IdempotencyKeyReusedException;
import com.sumup.bankaccount.core.exceptions.InsufficientFundsException;
import com.sumup.bankaccount.core.exceptions.SameAccountTransferException;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransferCommand;
import com.sumup.bankaccount.core.ports.primary.TransferService;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

  private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID TARGET_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final Currency EUR = Currency.getInstance("EUR");
  private static final String VALID_BODY =
      "{'sourceAccountId': '" + SOURCE_ID + "', 'targetAccountId': '" + TARGET_ID + "', 'amount': 2500}";

  @Autowired
  private MockMvc mvc;
  @MockitoBean
  private TransferService transferService;

  @Test
  void transferReturnsTheStoredTransfer() throws Exception {
    when(transferService.transfer(any())).thenReturn(
        Transaction.transfer(SOURCE_ID, TARGET_ID, 2_500, EUR, "key-1", Instant.parse("2026-11-02T10:15:30Z")));

    mvc.perform(post("/transfers").header("Idempotency-Key", "key-1").contentType(APPLICATION_JSON)
            .content(json(VALID_BODY)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("TRANSFER"))
        .andExpect(jsonPath("$.sourceAccountId").value(SOURCE_ID.toString()))
        .andExpect(jsonPath("$.targetAccountId").value(TARGET_ID.toString()))
        .andExpect(jsonPath("$.amount").value(2_500))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.createdAt").value("2026-11-02T10:15:30Z"));

    verify(transferService).transfer(new TransferCommand(SOURCE_ID, TARGET_ID, 2_500, "key-1"));
  }

  @Test
  void transferWithoutAKeyPassesNoKey() throws Exception {
    when(transferService.transfer(any())).thenReturn(
        Transaction.transfer(SOURCE_ID, TARGET_ID, 2_500, EUR, null, Instant.parse("2026-11-02T10:15:30Z")));

    mvc.perform(post("/transfers").contentType(APPLICATION_JSON).content(json(VALID_BODY)))
        .andExpect(status().isCreated());

    verify(transferService).transfer(new TransferCommand(SOURCE_ID, TARGET_ID, 2_500, null));
  }

  static Stream<Arguments> invalidTransferRequests() {
    return Stream.of(
        arguments("missing source", "{'targetAccountId': '" + TARGET_ID + "', 'amount': 1}", "sourceAccountId"),
        arguments("missing target", "{'sourceAccountId': '" + SOURCE_ID + "', 'amount': 1}", "targetAccountId"),
        arguments("missing amount",
            "{'sourceAccountId': '" + SOURCE_ID + "', 'targetAccountId': '" + TARGET_ID + "'}", "amount"),
        arguments("zero amount",
            "{'sourceAccountId': '" + SOURCE_ID + "', 'targetAccountId': '" + TARGET_ID + "', 'amount': 0}", "amount"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidTransferRequests")
  void transferRejectsInvalidFields(String description, String body, String invalidField) throws Exception {
    mvc.perform(post("/transfers").contentType(APPLICATION_JSON).content(json(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem(invalidField)));

    verifyNoInteractions(transferService);
  }

  @Test
  void transferRejectsAnAccountIdThatIsNotAUuid() throws Exception {
    mvc.perform(post("/transfers").contentType(APPLICATION_JSON)
            .content(json("{'sourceAccountId': 'abc', 'targetAccountId': '" + TARGET_ID + "', 'amount': 1}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    verifyNoInteractions(transferService);
  }

  static Stream<Arguments> businessErrors() {
    return Stream.of(
        arguments(new AccountNotFoundException(TARGET_ID), 404, "ACCOUNT_NOT_FOUND"),
        arguments(new SameAccountTransferException(SOURCE_ID), 422, "SAME_ACCOUNT_TRANSFER"),
        arguments(new CurrencyMismatchException(EUR, Currency.getInstance("USD")), 422, "CURRENCY_MISMATCH"),
        arguments(new InsufficientFundsException(SOURCE_ID), 422, "INSUFFICIENT_FUNDS"),
        arguments(new BalanceLimitExceededException(TARGET_ID), 422, "BALANCE_LIMIT_EXCEEDED"),
        arguments(new IdempotencyKeyReusedException("key-1"), 422, "IDEMPOTENCY_KEY_REUSED"),
        arguments(new IdempotencyKeyConflictException("key-1"), 409, "IDEMPOTENCY_KEY_CONFLICT"));
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("businessErrors")
  void transferMapsBusinessErrorsToProblemResponses(BankingException error, int status, String code)
      throws Exception {
    when(transferService.transfer(any())).thenThrow(error);

    mvc.perform(post("/transfers").contentType(APPLICATION_JSON).content(json(VALID_BODY)))
        .andExpect(status().is(status))
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.detail").value(error.getMessage()))
        .andExpect(jsonPath("$.instance").value("/transfers"));
  }

  /** Lets test JSON use single quotes, which keeps it readable inside Java strings. */
  private static String json(String singleQuoted) {
    return singleQuoted.replace('\'', '"');
  }
}
