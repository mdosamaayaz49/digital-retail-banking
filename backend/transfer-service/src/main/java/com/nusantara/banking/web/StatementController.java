package com.nusantara.banking.web;

import com.nusantara.banking.security.CurrentCustomer;
import com.nusantara.banking.service.StatementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementController {
  private final JdbcTemplate db;
  private final CurrentCustomer current;
  private final StatementService statements;

  public StatementController(
      JdbcTemplate db, CurrentCustomer current, StatementService statements) {
    this.db = db;
    this.current = current;
    this.statements = statements;
  }

  public record Generate(@NotNull UUID accountId, @NotNull LocalDate from, @NotNull LocalDate to) {}

  @GetMapping
  public List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "select s.*,a.name as account_name from bank_statements s join bank_accounts a on"
            + " a.id=s.account_id where s.customer_id=? order by s.created_at desc limit 100",
        current.id(jwt));
  }

  @PostMapping("/generate")
  public Map<String, Object> generate(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Generate request) {
    return statements.generate(current.id(jwt), request.accountId(), request.from(), request.to());
  }

  @GetMapping(value = "/{id}/download", produces = "application/pdf")
  public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement-" + id + ".pdf")
        .cacheControl(CacheControl.noStore())
        .body(statements.pdf(current.id(jwt), id));
  }
}
