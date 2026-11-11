package com.nusantara.banking.web;

import com.nusantara.banking.service.AccountOperations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/holds")
@PreAuthorize("hasRole('ADMIN')")
public class HoldController {
  private final AccountOperations operations;

  public HoldController(AccountOperations operations) {
    this.operations = operations;
  }

  public record Hold(
      @NotNull UUID accountId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 100) String reason) {}

  @PostMapping
  public Map<String, Object> place(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody Hold r) {
    return operations.hold(r.accountId(), key, r.amount(), r.reason(), jwt.getSubject());
  }

  @PostMapping("/{id}/release")
  public void release(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    operations.release(id, jwt.getSubject());
  }
}
