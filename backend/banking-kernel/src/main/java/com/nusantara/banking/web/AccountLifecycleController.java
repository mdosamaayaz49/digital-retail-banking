package com.nusantara.banking.web;

import com.nusantara.banking.security.CurrentCustomer;
import com.nusantara.banking.service.AccountOperations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountLifecycleController {
  private final AccountOperations operations;
  private final CurrentCustomer current;
  private final JdbcTemplate db;

  public AccountLifecycleController(
      AccountOperations operations, CurrentCustomer current, JdbcTemplate db) {
    this.operations = operations;
    this.current = current;
    this.db = db;
  }

  public record Open(
      @NotNull @Pattern(regexp = "SAVINGS|CURRENT") String type,
      @NotBlank @Size(max = 100) String name) {}

  @PostMapping
  public Map<String, Object> open(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Open request) {
    return operations.open(current.id(jwt), request.type(), request.name());
  }

  @PostMapping("/{id}/close")
  public void close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    operations.close(current.id(jwt), id);
  }

  @GetMapping("/{id}/holds")
  public List<Map<String, Object>> holds(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    var customer = current.id(jwt);
    if (db.queryForObject(
            "select count(*) from bank_accounts where id=? and customer_id=?",
            Integer.class,
            id,
            customer)
        == 0) throw new BankingException(404, "ACCOUNT_NOT_FOUND", "Account not found.");
    return db.queryForList(
        "select id,amount,reason,released_at from account_holds where account_id=? order by"
            + " released_at nulls first,id limit 100",
        id);
  }
}
