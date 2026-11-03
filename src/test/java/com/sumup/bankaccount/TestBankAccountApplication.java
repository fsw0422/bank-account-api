package com.sumup.bankaccount;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app on the host against a Testcontainers PostgreSQL, for IDE debugging.
 * Start with {@code ./gradlew bootTestRun} or run this class from the IDE.
 */
public class TestBankAccountApplication {

  public static void main(String[] args) {
    SpringApplication.from(BankAccountApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
