package com.nusantara.banking.web;

import com.nusantara.banking.dto.PageResult;
import com.nusantara.banking.repository.AccountRepository;
import com.nusantara.banking.security.CurrentCustomer;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class HistoryController {
  private final JdbcTemplate db;
  private final CurrentCustomer current;
  private final AccountRepository accounts;

  public HistoryController(JdbcTemplate db, CurrentCustomer current, AccountRepository accounts) {
    this.db = db;
    this.current = current;
    this.accounts = accounts;
  }

  @GetMapping("/accounts/{id}/transactions")
  public PageResult<Map<String, Object>> account(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "") @Size(max = 100) String search,
      @RequestParam(defaultValue = "2000-01-01") LocalDate from,
      @RequestParam(defaultValue = "2100-01-01") LocalDate to) {
    var customer = current.id(jwt);
    if (accounts.findByIdAndCustomerId(id, customer).isEmpty())
      throw new BankingException(404, "ACCOUNT_NOT_FOUND", "Account not found.");
    return query(customer, id, page, size, search, from, to);
  }

  @GetMapping("/transactions")
  public PageResult<Map<String, Object>> all(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "") @Size(max = 100) String search,
      @RequestParam(defaultValue = "2000-01-01") LocalDate from,
      @RequestParam(defaultValue = "2100-01-01") LocalDate to) {
    return query(current.id(jwt), null, page, size, search, from, to);
  }

  private PageResult<Map<String, Object>> query(
      UUID customer,
      UUID account,
      int page,
      int size,
      String search,
      LocalDate from,
      LocalDate to) {
    var where =
        " from ledger_entries l join transfers t on t.id=l.transfer_id join bank_accounts a on"
            + " a.id=l.account_id where a.customer_id=? and t.created_at>=?::date at time zone"
            + " 'Asia/Kuala_Lumpur' and t.created_at<(?::date+1) at time zone 'Asia/Kuala_Lumpur'"
            + " and (t.description ilike ? or t.reference ilike ?)"
            + (account == null ? "" : " and a.id=?");
    var args =
        new ArrayList<Object>(
            List.of(
                customer, from.toString(), to.toString(), "%" + search + "%", "%" + search + "%"));
    if (account != null) args.add(account);
    var total = db.queryForObject("select count(*)" + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var items =
        db.queryForList(
            "select t.id,t.reference,t.description,t.status,t.created_at,l.amount,a.name as"
                + " account_name,a.id as account_id,t.currency"
                + where
                + " order by t.created_at desc,l.id desc limit ? offset ?",
            args.toArray());
    return new PageResult<>(items, page, size, total == null ? 0 : total);
  }

  @GetMapping("/notifications")
  public List<Map<String, Object>> notifications(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "select event_id,event_type,message,created_at from notification_records where"
            + " customer_id=? order by created_at desc limit 50",
        current.id(jwt));
  }
}
