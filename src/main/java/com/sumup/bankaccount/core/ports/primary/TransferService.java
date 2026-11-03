package com.sumup.bankaccount.core.ports.primary;

import com.sumup.bankaccount.core.models.Transaction;
import com.sumup.bankaccount.core.models.TransferCommand;

public interface TransferService {

  /**
   * Moves money between two accounts atomically. Returns the stored transfer, or the original one
   * for a retried key.
   */
  Transaction transfer(TransferCommand command);
}
