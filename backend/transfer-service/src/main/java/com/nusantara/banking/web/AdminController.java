package com.nusantara.banking.web;

import com.nusantara.banking.dto.PageResult;
import com.nusantara.banking.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {
  private final JdbcTemplate db;
  private final EventService events;
  private final TransferService transfers;

  public AdminController(JdbcTemplate db, EventService events, TransferService transfers) {
    this.db = db;
    this.events = events;
    this.transfers = transfers;
  }

  @GetMapping("/customers")
  public PageResult<Map<String, Object>> customers(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return page("customers", "created_at", page, size);
  }

  @GetMapping("/accounts")
  public PageResult<Map<String, Object>> accounts(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return new PageResult<>(
        db.queryForList(
            "select a.*,c.display_name from bank_accounts a join customers c on c.id=a.customer_id"
                + " order by a.account_number limit ? offset ?",
            size,
            (long) page * size),
        page,
        size,
        db.queryForObject(
            "select count(*) from bank_accounts where customer_id is not null", Long.class));
  }

  @GetMapping("/transactions")
  public PageResult<Map<String, Object>> transactions(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return page("transfers", "created_at", page, size);
  }

  @GetMapping("/audit-logs")
  public PageResult<Map<String, Object>> audit(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return page("audit_logs", "created_at", page, size);
  }

  @GetMapping("/summary")
  public Map<String, Object> summary() {
    return Map.of(
        "customers",
        db.queryForObject("select count(*) from customers", Long.class),
        "accounts",
        db.queryForObject(
            "select count(*) from bank_accounts where customer_id is not null", Long.class),
        "transfers",
        db.queryForObject(
            "select count(*) from transfers where customer_id is not null", Long.class),
        "pendingKyc",
        db.queryForObject("select count(*) from customers where kyc_status='PENDING'", Long.class),
        "pendingEvents",
        db.queryForObject(
            "select count(*) from outbox_events where published_at is null", Long.class));
  }

  public record Kyc(@Pattern(regexp = "VERIFIED|REJECTED|PENDING") @NotNull String status) {}

  public record State(@Pattern(regexp = "ACTIVE|FROZEN") @NotNull String status) {}

  @PatchMapping("/customers/{id}/kyc")
  @Transactional
  public void kyc(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Kyc request) {
    if (db.update("update customers set kyc_status=? where id=?", request.status(), id) == 0)
      throw new BankingException(404, "CUSTOMER_NOT_FOUND", "Customer not found.");
    events.audit(jwt.getSubject(), "KYC_" + request.status(), id);
  }

  @PatchMapping("/accounts/{id}/status")
  @Transactional
  public void status(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody State request) {
    var accounts =
        db.queryForList(
            "select customer_id from bank_accounts where id=? and customer_id is not null for"
                + " update",
            id);
    if (accounts.isEmpty())
      throw new BankingException(404, "ACCOUNT_NOT_FOUND", "Account not found.");
    db.update(
        "update bank_accounts set status=?,version=version+1 where id=?", request.status(), id);
    events.audit(jwt.getSubject(), "ACCOUNT_" + request.status(), id);
    events.event(
        (UUID) accounts.getFirst().get("customer_id"),
        id,
        "ACCOUNT_" + request.status(),
        "Your account status changed to " + request.status() + ".");
  }

  @PostMapping("/transfers/{id}/reverse")
  public Map<String, Object> reverse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return transfers.reverse(id, jwt.getSubject());
  }

  private PageResult<Map<String, Object>> page(String table, String sort, int page, int size) {
    return new PageResult<>(
        db.queryForList(
            "select * from " + table + " order by " + sort + " desc limit ? offset ?",
            size,
            (long) page * size),
        page,
        size,
        db.queryForObject("select count(*) from " + table, Long.class));
  }
}
