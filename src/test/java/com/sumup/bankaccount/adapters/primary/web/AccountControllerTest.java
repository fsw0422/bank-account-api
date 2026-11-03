package com.sumup.bankaccount.adapters.primary.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sumup.bankaccount.core.exceptions.AccountNotFoundException;
import com.sumup.bankaccount.core.models.Account;
import com.sumup.bankaccount.core.models.CreateAccountCommand;
import com.sumup.bankaccount.core.models.DepositCommand;
import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.ports.primary.AccountService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

  private static final UUID ACCOUNT_ID = UUID.fromString("7f8d2c1e-3b4a-4c5d-9e6f-0a1b2c3d4e5f");
  private static final Instant CREATED_AT = Instant.parse("2026-11-02T10:15:30.123456Z");
  private static final Currency EUR = Currency.getInstance("EUR");

  @Autowired
  private MockMvc mvc;
  @MockitoBean
  private AccountService accountService;

  @Test
  void createAccountReturnsTheAccountWithItsLocation() throws Exception {
    when(accountService.createAccount(any())).thenReturn(account(10_000));

    mvc.perform(post("/accounts").contentType(APPLICATION_JSON)
            .content(json("{'ownerName': '  Ada Lovelace ', 'currency': 'EUR', 'initialBalance': 10000}")))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/accounts/" + ACCOUNT_ID))
        .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
        .andExpect(jsonPath("$.ownerName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.balance").value(10_000))
        .andExpect(jsonPath("$.createdAt").value("2026-11-02T10:15:30.123456Z"));

    verify(accountService).createAccount(new CreateAccountCommand("Ada Lovelace", EUR, 10_000));
  }

  @Test
  void createAccountDefaultsTheInitialBalanceToZero() throws Exception {
    when(accountService.createAccount(any())).thenReturn(account(0));

    mvc.perform(post("/accounts").contentType(APPLICATION_JSON)
            .content(json("{'ownerName': 'Ada Lovelace', 'currency': 'EUR'}")))
        .andExpect(status().isCreated());

    verify(accountService).createAccount(new CreateAccountCommand("Ada Lovelace", EUR, 0));
  }

  static Stream<Arguments> invalidCreateAccountRequests() {
    return Stream.of(
        arguments("missing owner", "{'currency': 'EUR'}", "ownerName"),
        arguments("blank owner", "{'ownerName': '   ', 'currency': 'EUR'}", "ownerName"),
        arguments("owner too long", "{'ownerName': '" + "a".repeat(101) + "', 'currency': 'EUR'}", "ownerName"),
        arguments("missing currency", "{'ownerName': 'Ada'}", "currency"),
        arguments("lowercase currency", "{'ownerName': 'Ada', 'currency': 'eur'}", "currency"),
        arguments("unknown currency", "{'ownerName': 'Ada', 'currency': 'ABC'}", "currency"),
        arguments("currency without minor units", "{'ownerName': 'Ada', 'currency': 'XAU'}", "currency"),
        arguments("negative initial balance", "{'ownerName': 'Ada', 'currency': 'EUR', 'initialBalance': -1}",
            "initialBalance"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidCreateAccountRequests")
  void createAccountRejectsInvalidFields(String description, String body, String invalidField) throws Exception {
    mvc.perform(post("/accounts").contentType(APPLICATION_JSON).content(json(body)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem(invalidField)));

    verifyNoInteractions(accountService);
  }

  static Stream<Arguments> unreadableBodies() {
    return Stream.of(
        arguments("decimal amount", "{'ownerName': 'Ada', 'currency': 'EUR', 'initialBalance': 10.5}"),
        arguments("amount too large", "{'ownerName': 'Ada', 'currency': 'EUR', 'initialBalance': 99999999999999999999}"),
        arguments("malformed JSON", "{'ownerName': "),
        arguments("empty body", ""));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unreadableBodies")
  void createAccountRejectsUnreadableBodies(String description, String body) throws Exception {
    mvc.perform(post("/accounts").contentType(APPLICATION_JSON).content(json(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    verifyNoInteractions(accountService);
  }

  @Test
  void createAccountRejectsBodiesThatAreNotJson() throws Exception {
    mvc.perform(post("/accounts").contentType(TEXT_PLAIN).content("Ada, EUR"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void getAccountReturnsTheCurrentBalance() throws Exception {
    when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account(4_200));

    mvc.perform(get("/accounts/{accountId}", ACCOUNT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
        .andExpect(jsonPath("$.balance").value(4_200));
  }

  @Test
  void getAccountReturnsNotFoundForAnUnknownId() throws Exception {
    when(accountService.getAccount(ACCOUNT_ID)).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

    mvc.perform(get("/accounts/{accountId}", ACCOUNT_ID))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Account " + ACCOUNT_ID + " does not exist."))
        .andExpect(jsonPath("$.instance").value("/accounts/" + ACCOUNT_ID));
  }

  @Test
  void getAccountRejectsAnIdThatIsNotAUuid() throws Exception {
    mvc.perform(get("/accounts/{accountId}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void depositReturnsTheDepositAndPassesTheIdempotencyKey() throws Exception {
    when(accountService.deposit(any()))
        .thenReturn(Transaction.deposit(ACCOUNT_ID, 500, EUR, "key-1", CREATED_AT));

    mvc.perform(post("/accounts/{accountId}/deposits", ACCOUNT_ID)
            .header("Idempotency-Key", "key-1")
            .contentType(APPLICATION_JSON)
            .content(json("{'amount': 500}")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("DEPOSIT"))
        .andExpect(jsonPath("$.sourceAccountId").value(nullValue()))
        .andExpect(jsonPath("$.targetAccountId").value(ACCOUNT_ID.toString()))
        .andExpect(jsonPath("$.amount").value(500))
        .andExpect(jsonPath("$.currency").value("EUR"));

    verify(accountService).deposit(new DepositCommand(ACCOUNT_ID, 500, "key-1"));
  }

  static Stream<Arguments> invalidDepositAmounts() {
    return Stream.of(arguments("{'amount': 0}"), arguments("{'amount': -5}"), arguments("{}"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidDepositAmounts")
  void depositRejectsAMissingOrNonPositiveAmount(String body) throws Exception {
    mvc.perform(post("/accounts/{accountId}/deposits", ACCOUNT_ID).contentType(APPLICATION_JSON).content(json(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("amount")));

    verifyNoInteractions(accountService);
  }

  @Test
  void depositRejectsAnIdempotencyKeyThatIsTooLong() throws Exception {
    mvc.perform(post("/accounts/{accountId}/deposits", ACCOUNT_ID)
            .header("Idempotency-Key", "k".repeat(256))
            .contentType(APPLICATION_JSON)
            .content(json("{'amount': 500}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("Idempotency-Key")));

    verifyNoInteractions(accountService);
  }

  @Test
  void unexpectedErrorsReturnNoInternalDetails() throws Exception {
    when(accountService.getAccount(ACCOUNT_ID)).thenThrow(new IllegalStateException("password=hunter2"));

    mvc.perform(get("/accounts/{accountId}", ACCOUNT_ID))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("hunter2"))));
  }

  static Stream<Arguments> databaseOutages() {
    return Stream.of(
        arguments(new CannotGetJdbcConnectionException("database down")),
        // What real outages raise: Spring cannot open the database transaction, or a dead pooled
        // connection makes the rollback fail.
        arguments(new CannotCreateTransactionException("Could not open JDBC Connection for transaction")),
        arguments(new TransactionSystemException("JDBC rollback failed")));
  }

  @ParameterizedTest
  @MethodSource("databaseOutages")
  void databaseOutagesAskTheClientToRetry(RuntimeException outage) throws Exception {
    when(accountService.getAccount(ACCOUNT_ID)).thenThrow(outage);

    mvc.perform(get("/accounts/{accountId}", ACCOUNT_ID))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
  }

  private static Account account(long balance) {
    return new Account(ACCOUNT_ID, "Ada Lovelace", EUR, balance, CREATED_AT);
  }

  /** Lets test JSON use single quotes, which keeps it readable inside Java strings. */
  private static String json(String singleQuoted) {
    return singleQuoted.replace('\'', '"');
  }
}
