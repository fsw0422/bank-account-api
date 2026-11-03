package com.sumup.bankaccount;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Keeps the hexagonal layout honest: the business rules never depend on HTTP or SQL details. */
@AnalyzeClasses(packages = "com.sumup.bankaccount", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule coreDoesNotDependOnAdapters = noClasses()
      .that().resideInAPackage("..core..")
      .should().dependOnClassesThat().resideInAPackage("..adapters..");

  /**
   * The Bean Validation API (jakarta.validation) is allowed: the core defines constraints such as
   * {@code @SupportedCurrency}. Its implementation (Hibernate Validator) is not.
   */
  @ArchTest
  static final ArchRule coreDoesNotUseWebOrDatabaseLibraries = noClasses()
      .that().resideInAPackage("..core..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "org.springframework.web..",
          "org.springframework.http..",
          "org.springframework.jdbc..",
          "jakarta.servlet..",
          "org.hibernate..",
          "tools.jackson..",
          "org.jooq..",
          "java.sql..");

  @ArchTest
  static final ArchRule primaryAdaptersDoNotUseSecondaryAdapters = noClasses()
      .that().resideInAPackage("..adapters.primary..")
      .should().dependOnClassesThat().resideInAPackage("..adapters.secondary..");

  @ArchTest
  static final ArchRule secondaryAdaptersDoNotUsePrimaryAdapters = noClasses()
      .that().resideInAPackage("..adapters.secondary..")
      .should().dependOnClassesThat().resideInAPackage("..adapters.primary..");
}
