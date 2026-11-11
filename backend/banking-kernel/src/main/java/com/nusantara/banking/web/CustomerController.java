package com.nusantara.banking.web;

import com.nusantara.banking.dto.BankingResponses.Profile;
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
@RequestMapping("/api/v1/customers/me")
public class CustomerController {
  private final JdbcTemplate db;
  private final CurrentCustomer current;
  private final EventService events;

  public CustomerController(JdbcTemplate db, CurrentCustomer current, EventService events) {
    this.db = db;
    this.current = current;
    this.events = events;
  }

  public record ProfileUpdate(
      @NotBlank @Size(max = 100) String displayName,
      @NotBlank @Email @Size(max = 200) String email,
      @Pattern(regexp = "|[+0-9 -]{5,30}") String phone) {}

  @GetMapping
  public Profile get(@AuthenticationPrincipal Jwt jwt) {
    return Profile.from(db.queryForMap("select * from customers where id=?", current.id(jwt)));
  }

  @PostMapping
  @Transactional
  public Profile provision(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdate p) {
    var existing = db.queryForList("select * from customers where subject=?", jwt.getSubject());
    if (!existing.isEmpty()) return Profile.from(existing.getFirst());
    var id = UUID.randomUUID();
    db.update(
        "insert into customers(id,subject,display_name,email,phone) values (?,?,?,?,?)",
        id,
        jwt.getSubject(),
        p.displayName(),
        p.email(),
        p.phone());
    events.audit(jwt.getSubject(), "CUSTOMER_CREATED", id);
    return Profile.from(db.queryForMap("select * from customers where id=?", id));
  }

  @PatchMapping
  @Transactional
  public Profile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdate p) {
    var id = current.id(jwt);
    db.update(
        "update customers set display_name=?,email=?,phone=? where id=?",
        p.displayName(),
        p.email(),
        p.phone(),
        id);
    events.audit(jwt.getSubject(), "PROFILE_UPDATED", id);
    return Profile.from(db.queryForMap("select * from customers where id=?", id));
  }
}
