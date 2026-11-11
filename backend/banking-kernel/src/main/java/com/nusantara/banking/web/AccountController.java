package com.nusantara.banking.web;

import com.nusantara.banking.dto.AccountResponse;
import com.nusantara.banking.repository.AccountRepository;
import com.nusantara.banking.security.CurrentCustomer;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
  private final AccountRepository accounts;
  private final CurrentCustomer current;

  public AccountController(AccountRepository accounts, CurrentCustomer current) {
    this.accounts = accounts;
    this.current = current;
  }

  @GetMapping
  public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return accounts.findByCustomerIdOrderByAccountNumber(current.id(jwt)).stream()
        .map(AccountResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return accounts
        .findByIdAndCustomerId(id, current.id(jwt))
        .map(AccountResponse::from)
        .orElseThrow(() -> new BankingException(404, "ACCOUNT_NOT_FOUND", "Account not found."));
  }
}
