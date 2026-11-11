package com.nusantara.banking.web;

import com.nusantara.banking.dto.*;
import com.nusantara.banking.dto.BankingResponses.Transfer;
import com.nusantara.banking.security.CurrentCustomer;
import com.nusantara.banking.service.TransferService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
  private final TransferService transfers;
  private final CurrentCustomer current;
  private final JdbcTemplate db;
  private final com.nusantara.banking.service.RejectedTransferRecorder failures;

  public TransferController(
      TransferService transfers,
      CurrentCustomer current,
      JdbcTemplate db,
      com.nusantara.banking.service.RejectedTransferRecorder failures) {
    this.transfers = transfers;
    this.current = current;
    this.db = db;
    this.failures = failures;
  }

  @PostMapping
  public Transfer create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody TransferRequest request) {
    var customer = current.id(jwt);
    try {
      return Transfer.from(transfers.transfer(customer, key, request));
    } catch (BankingException e) {
      failures.record(customer, key, e.code);
      throw e;
    }
  }

  @GetMapping("/{id}")
  public Transfer get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    var customer = current.id(jwt);
    var rows =
        db.queryForList(
            "select t.* from transfers t where t.id=? and (t.customer_id=? or exists(select 1 from"
                + " bank_accounts a where a.id in (t.source_id,t.destination_id) and"
                + " a.customer_id=?))",
            id,
            customer,
            customer);
    if (rows.isEmpty())
      throw new BankingException(404, "TRANSFER_NOT_FOUND", "Transfer not found.");
    return Transfer.from(rows.getFirst());
  }
}
