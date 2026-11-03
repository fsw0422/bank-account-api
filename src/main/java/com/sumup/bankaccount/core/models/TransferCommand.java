package com.sumup.bankaccount.core.models;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** @param amount in minor units of the accounts' currency, positive */
public record TransferCommand(
    UUID sourceAccountId, UUID targetAccountId, long amount, @Nullable String idempotencyKey) {}
