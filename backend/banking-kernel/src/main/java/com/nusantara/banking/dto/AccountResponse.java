package com.nusantara.banking.dto;

import com.nusantara.banking.domain.Account;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String accountNumber,
    String name,
    String type,
    String status,
    String currency,
    BigDecimal balance,
    BigDecimal held,
    BigDecimal availableBalance) {
  public static AccountResponse from(Account a) {
    return new AccountResponse(
        a.getId(),
        a.getAccountNumber(),
        a.getName(),
        a.getType(),
        a.getStatus(),
        a.getCurrency(),
        a.getBalance(),
        a.getHeld(),
        a.getAvailableBalance());
  }
}
