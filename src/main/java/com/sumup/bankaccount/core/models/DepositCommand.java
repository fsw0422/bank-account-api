package com.sumup.bankaccount.core.models;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** @param amount in minor units of the account currency, positive */
public record DepositCommand(UUID accountId, long amount, @Nullable String idempotencyKey) {}
