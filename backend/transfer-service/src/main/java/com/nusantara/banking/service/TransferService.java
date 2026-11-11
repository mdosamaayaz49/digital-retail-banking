package com.nusantara.banking.service;

import com.nusantara.banking.dto.TransferRequest;
import com.nusantara.banking.web.BankingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
  private final JdbcTemplate db;
  private final EventService events;

  public TransferService(JdbcTemplate db, EventService events) {
    this.db = db;
    this.events = events;
  }

  @Transactional
  public Map<String, Object> transfer(UUID customer, UUID key, TransferRequest request) {
    // Serializing a customer's requests makes idempotency and daily-limit checks atomic.
    var kyc =
        db.queryForObject(
            "select kyc_status from customers where id=? for update", String.class, customer);
    var hash = fingerprint(request);
    var previous =
        db.queryForList(
            "select * from transfers where customer_id=? and idempotency_key=?", customer, key);
    if (!previous.isEmpty()) {
      var p = previous.getFirst();
      if (!hash.equals(p.get("request_hash")))
        throw error(409, "IDEMPOTENCY_CONFLICT", "This key was already used for another request.");
      return p;
    }
    if (!"VERIFIED".equals(kyc))
      throw error(403, "KYC_REQUIRED", "Simulated KYC verification is required.");
    if (request.amount().signum() <= 0
        || request.amount().scale() > 2
        || request.amount().compareTo(new BigDecimal("10000.00")) > 0)
      throw error(
          422,
          "INVALID_AMOUNT",
          "Amount must be between RM 0.01 and RM 10,000.00 with at most two decimals.");
    if (request.sourceId().equals(request.destinationId()))
      throw error(422, "SAME_ACCOUNT", "Choose a different destination account.");
    var accounts = lockAccounts(request.sourceId(), request.destinationId());
    var source = find(accounts, request.sourceId());
    var destination = find(accounts, request.destinationId());
    if (!customer.equals(source.get("customer_id")))
      throw error(404, "ACCOUNT_NOT_FOUND", "Account not found.");
    if (destination.get("customer_id") == null)
      throw error(
          422, "INVALID_DESTINATION", "This destination cannot receive a customer transfer.");
    if (!customer.equals(destination.get("customer_id"))
        && db.queryForObject(
                "select count(*) from beneficiaries where customer_id=? and account_id=?",
                Integer.class,
                customer,
                request.destinationId())
            == 0)
      throw error(422, "INVALID_BENEFICIARY", "Register this beneficiary before transferring.");
    var daily =
        db.queryForObject(
            "select coalesce(sum(amount),0) from transfers where customer_id=? and reversal_of is"
                + " null and created_at>=date_trunc('day',now() at time zone 'Asia/Kuala_Lumpur')"
                + " at time zone 'Asia/Kuala_Lumpur'",
            BigDecimal.class,
            customer);
    if (daily.add(request.amount()).compareTo(new BigDecimal("25000.00")) > 0)
      throw error(422, "DAILY_LIMIT", "The simulated daily transfer limit is RM 25,000.00.");
    validateAccounts(source, destination, request.amount());
    var id =
        post(
            customer,
            key,
            hash,
            request.sourceId(),
            request.destinationId(),
            request.amount(),
            request.description(),
            null);
    events.audit(customer.toString(), "TRANSFER_SETTLED", id);
    events.event(
        customer,
        id,
        "TRANSFER_COMPLETED",
        "Your transfer of MYR " + request.amount().toPlainString() + " is complete.");
    var recipient = (UUID) destination.get("customer_id");
    if (!customer.equals(recipient))
      events.event(
          recipient,
          id,
          "TRANSFER_RECEIVED",
          "You received MYR " + request.amount().toPlainString() + " in simulated funds.");
    return byId(id);
  }

  @Transactional
  public Map<String, Object> reverse(UUID originalId, String actor) {
    var original =
        db.queryForList(
            "select * from transfers where id=? and customer_id is not null for update",
            originalId);
    if (original.isEmpty()) throw error(404, "TRANSFER_NOT_FOUND", "Transfer not found.");
    var t = original.getFirst();
    if (t.get("reversal_of") != null)
      throw error(409, "INVALID_REVERSAL", "A reversal cannot itself be reversed.");
    if ("REVERSED".equals(t.get("status")))
      return db.queryForMap("select * from transfers where reversal_of=?", originalId);
    UUID from = (UUID) t.get("destination_id"), to = (UUID) t.get("source_id");
    var accounts = lockAccounts(from, to);
    var amount = (BigDecimal) t.get("amount");
    validateAccounts(find(accounts, from), find(accounts, to), amount);
    var id =
        post(
            (UUID) t.get("customer_id"),
            UUID.randomUUID(),
            "reversal:" + originalId,
            from,
            to,
            amount,
            "Reversal of " + t.get("reference"),
            originalId);
    db.update("update transfers set status='REVERSED' where id=?", originalId);
    events.audit(actor, "TRANSFER_REVERSED", originalId);
    events.event(
        (UUID) t.get("customer_id"),
        id,
        "TRANSFER_REVERSED",
        "Transfer " + t.get("reference") + " was reversed with compensating entries.");
    return byId(id);
  }

  private List<Map<String, Object>> lockAccounts(UUID a, UUID b) {
    return db.queryForList(
        "select * from bank_accounts where id in (?,?) order by id for update", a, b);
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, UUID id) {
    return rows.stream()
        .filter(a -> id.equals(a.get("id")))
        .findFirst()
        .orElseThrow(() -> error(404, "ACCOUNT_NOT_FOUND", "Account not found."));
  }

  private void validateAccounts(
      Map<String, Object> source, Map<String, Object> destination, BigDecimal amount) {
    if (!"ACTIVE".equals(source.get("status")) || !"ACTIVE".equals(destination.get("status")))
      throw error(422, "ACCOUNT_INACTIVE", "Both accounts must be active.");
    if (((BigDecimal) source.get("balance"))
            .subtract((BigDecimal) source.get("held"))
            .compareTo(amount)
        < 0)
      throw error(
          422, "INSUFFICIENT_FUNDS", "There are insufficient available funds in this account.");
  }

  private UUID post(
      UUID customer,
      UUID key,
      String hash,
      UUID from,
      UUID to,
      BigDecimal amount,
      String description,
      UUID reversal) {
    var id = UUID.randomUUID();
    var ref = "NT-" + id.toString().replace("-", "").toUpperCase(Locale.ROOT);
    db.update(
        "insert into"
            + " transfers(id,customer_id,source_id,destination_id,amount,reference,description,status,idempotency_key,request_hash,reversal_of)"
            + " values (?,?,?,?,?,?,?,'SETTLED',?,?,?)",
        id,
        customer,
        from,
        to,
        amount,
        ref,
        description,
        key,
        hash,
        reversal);
    db.update(
        "update bank_accounts set balance=balance-?,version=version+1 where id=?", amount, from);
    db.update(
        "update bank_accounts set balance=balance+?,version=version+1 where id=?", amount, to);
    db.update(
        "insert into ledger_entries(transfer_id,account_id,amount) values (?,?,?),(?,?,?)",
        id,
        from,
        amount.negate(),
        id,
        to,
        amount);
    return id;
  }

  public Map<String, Object> byId(UUID id) {
    return db.queryForMap("select * from transfers where id=?", id);
  }

  private String fingerprint(TransferRequest r) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (r.sourceId()
                              + "|"
                              + r.destinationId()
                              + "|"
                              + r.amount().stripTrailingZeros().toPlainString()
                              + "|"
                              + r.description())
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private BankingException error(int status, String code, String message) {
    return new BankingException(status, code, message);
  }
}
