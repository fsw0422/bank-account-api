package com.sumup.bankaccount.adapters.secondary.postgres.support;

import org.jooq.DSLContext;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Empties the tables before each test, so every test starts from a known state and its writes are
 * really committed, as in production. (A test-managed transaction that rolls back would never
 * commit anything.) The PostgreSQL container itself comes from TestcontainersConfiguration.
 */
public final class CleanDatabaseExtension implements BeforeEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    var dsl = SpringExtension.getApplicationContext(context).getBean(DSLContext.class);
    RepositoryTestHelper.deleteAllRows(dsl);
  }
}
