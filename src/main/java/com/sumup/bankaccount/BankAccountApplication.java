package com.sumup.bankaccount;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BankAccountApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankAccountApplication.class, args);
  }

  /** One clock for all timestamps, so tests can use a fixed one. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
