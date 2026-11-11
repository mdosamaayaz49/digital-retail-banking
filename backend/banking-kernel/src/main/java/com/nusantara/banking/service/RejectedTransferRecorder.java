package com.nusantara.banking.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RejectedTransferRecorder {
  private final JdbcTemplate db;
  private final EventService events;

  public RejectedTransferRecorder(JdbcTemplate db, EventService events) {
    this.db = db;
    this.events = events;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID customer, UUID key, String code) {
    var id = UUID.randomUUID();
    if (db.update(
            "insert into rejected_requests(id,customer_id,idempotency_key,code) values (?,?,?,?) on"
                + " conflict(customer_id,idempotency_key,code) do nothing",
            id,
            customer,
            key,
            code)
        > 0) {
      events.audit(customer.toString(), "TRANSFER_REJECTED", id);
      events.event(
          customer,
          id,
          "TRANSFER_FAILED",
          "A simulated transfer request was rejected (" + code + "). No funds were moved.");
    }
  }
}
