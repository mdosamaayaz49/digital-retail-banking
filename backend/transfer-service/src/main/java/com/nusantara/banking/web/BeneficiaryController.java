package com.nusantara.banking.web;

import com.nusantara.banking.security.CurrentCustomer;
import com.nusantara.banking.service.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {
  private final JdbcTemplate db;
  private final CurrentCustomer current;
  private final EventService events;

  public BeneficiaryController(JdbcTemplate db, CurrentCustomer current, EventService events) {
    this.db = db;
    this.current = current;
    this.events = events;
  }

  public record Create(
      @NotBlank @Size(max = 20) String accountNumber, @NotBlank @Size(max = 80) String nickname) {}

  @GetMapping
  public List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "select b.*,a.account_number from beneficiaries b join bank_accounts a on a.id=b.account_id"
            + " where b.customer_id=? order by b.created_at desc",
        current.id(jwt));
  }

  @PostMapping
  @Transactional
  public Map<String, Object> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create request) {
    var customer = current.id(jwt);
    var account =
        db.queryForList(
            "select id from bank_accounts where account_number=? and customer_id is not null and"
                + " customer_id<>? and status='ACTIVE'",
            request.accountNumber(),
            customer);
    if (account.isEmpty())
      throw new BankingException(
          422, "INVALID_BENEFICIARY", "No eligible fictional account matches this account number.");
    var id = UUID.randomUUID();
    db.update(
        "insert into beneficiaries(id,customer_id,account_id,nickname) values (?,?,?,?)",
        id,
        customer,
        account.getFirst().get("id"),
        request.nickname());
    events.audit(jwt.getSubject(), "BENEFICIARY_ADDED", id);
    events.event(customer, id, "BENEFICIARY_ADDED", "A new fictional beneficiary was added.");
    return db.queryForMap("select * from beneficiaries where id=?", id);
  }

  @DeleteMapping("/{id}")
  @Transactional
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    if (db.update("delete from beneficiaries where id=? and customer_id=?", id, current.id(jwt))
        == 0) throw new BankingException(404, "BENEFICIARY_NOT_FOUND", "Beneficiary not found.");
    events.audit(jwt.getSubject(), "BENEFICIARY_REMOVED", id);
  }
}
