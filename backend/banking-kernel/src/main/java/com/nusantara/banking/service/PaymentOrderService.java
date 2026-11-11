package com.nusantara.banking.service;

import com.nusantara.banking.dto.*;
import com.nusantara.banking.web.BankingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderService {
  private final JdbcTemplate db;
  private final TransferService transfers;
  private final EventService events;

  public PaymentOrderService(JdbcTemplate db, TransferService transfers, EventService events) {
    this.db = db;
    this.transfers = transfers;
    this.events = events;
  }

  @Transactional
  public Map<String, Object> create(UUID customer, UUID key, OrderRequest r) {
    var kyc =
        db.queryForObject(
            "select kyc_status from customers where id=? for update", String.class, customer);
    var hash = fingerprint(r);
    var existing =
        db.queryForList(
            "select * from payment_orders where customer_id=? and idempotency_key=?",
            customer,
            key);
    if (!existing.isEmpty()) {
      var e = existing.getFirst();
      if (!hash.equals(e.get("request_hash")))
        throw error(
            409, "IDEMPOTENCY_CONFLICT", "The key was already used for another payment order.");
      return e;
    }
    if (!"VERIFIED".equals(kyc))
      throw error(403, "KYC_REQUIRED", "Simulated KYC verification is required.");
    var now = Instant.now();
    if (r.scheduledAt().isBefore(now.minusSeconds(60))
        || r.scheduledAt().isAfter(now.plus(Duration.ofDays(90))))
      throw error(422, "INVALID_SCHEDULE", "Choose a settlement time from now to 90 days ahead.");
    if (r.amount() == null
        || r.amount().signum() <= 0
        || r.amount().scale() > 2
        || r.amount().compareTo(new BigDecimal("10000.00")) > 0)
      throw error(422, "INVALID_AMOUNT", "Use RM 0.01 to RM 10,000.00 with at most two decimals.");
    UUID destination = r.destinationId();
    String outcome = "ACCEPT";
    if ("DUITNOW_MOCK".equals(r.channel())) {
      var aliases =
          db.queryForList(
              "select * from mock_payment_aliases where alias=?",
              r.alias() == null ? "" : r.alias().toLowerCase(Locale.ROOT).trim());
      if (aliases.isEmpty())
        throw error(422, "ALIAS_NOT_FOUND", "This fictional mock-network alias was not found.");
      var alias = aliases.getFirst();
      destination = (UUID) alias.get("account_id");
      outcome = alias.get("outcome").toString();
    } else if (!"SCHEDULED".equals(r.channel()))
      throw error(400, "INVALID_CHANNEL", "Choose a supported simulated payment channel.");
    if (destination == null || destination.equals(r.sourceId()))
      throw error(422, "INVALID_DESTINATION", "Choose a different destination account.");
    var rows =
        db.queryForList(
            "select * from bank_accounts where id in (?,?) order by id for update",
            r.sourceId(),
            destination);
    var source =
        rows.stream()
            .filter(a -> r.sourceId().equals(a.get("id")) && customer.equals(a.get("customer_id")))
            .findFirst()
            .orElseThrow(() -> error(404, "ACCOUNT_NOT_FOUND", "Account not found."));
    final UUID destinationId = destination;
    var dest =
        rows.stream()
            .filter(a -> destinationId.equals(a.get("id")) && a.get("customer_id") != null)
            .findFirst()
            .orElseThrow(() -> error(422, "INVALID_DESTINATION", "Destination not found."));
    if (!"ACTIVE".equals(source.get("status")) || !"ACTIVE".equals(dest.get("status")))
      throw error(422, "ACCOUNT_INACTIVE", "Both accounts must be active.");
    if (!customer.equals(dest.get("customer_id"))
        && db.queryForObject(
                "select count(*) from beneficiaries where customer_id=? and account_id=?",
                Integer.class,
                customer,
                destination)
            == 0)
      throw error(
          422, "INVALID_BENEFICIARY", "Register the fictional destination as a beneficiary first.");
    if (((BigDecimal) source.get("balance"))
            .subtract((BigDecimal) source.get("held"))
            .compareTo(r.amount())
        < 0)
      throw error(
          422,
          "INSUFFICIENT_FUNDS",
          "There are insufficient available funds to reserve this payment.");
    var day = r.scheduledAt().atZone(ZoneId.of("Asia/Kuala_Lumpur")).toLocalDate();
    var start = day.atStartOfDay(ZoneId.of("Asia/Kuala_Lumpur")).toInstant();
    var end = day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kuala_Lumpur")).toInstant();
    var reserved =
        db.queryForObject(
            "select coalesce(sum(amount),0) from payment_orders where customer_id=? and"
                + " status='PENDING' and scheduled_at>=? and scheduled_at<?",
            BigDecimal.class,
            customer,
            java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(end));
    var sent =
        db.queryForObject(
            "select coalesce(sum(amount),0) from transfers where customer_id=? and reversal_of is"
                + " null and created_at>=? and created_at<?",
            BigDecimal.class,
            customer,
            java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(end));
    if (reserved.add(sent).add(r.amount()).compareTo(new BigDecimal("25000.00")) > 0)
      throw error(422, "DAILY_LIMIT", "The scheduled day's gross transfer limit is RM 25,000.00.");
    var id = UUID.randomUUID();
    var hold = UUID.randomUUID();
    db.update(
        "insert into account_holds(id,account_id,amount,reason,customer_id) values (?,?,?,?,?)",
        hold,
        r.sourceId(),
        r.amount(),
        "Payment order " + id,
        customer);
    db.update(
        "update bank_accounts set held=held+?,version=version+1 where id=?",
        r.amount(),
        r.sourceId());
    db.update(
        "insert into"
            + " payment_orders(id,customer_id,source_id,destination_id,amount,description,channel,alias,expected_outcome,status,scheduled_at,hold_id,idempotency_key,request_hash)"
            + " values (?,?,?,?,?,?,?,?,?,'PENDING',?,?,?,?)",
        id,
        customer,
        r.sourceId(),
        destination,
        r.amount(),
        r.description(),
        r.channel(),
        r.alias(),
        outcome,
        java.sql.Timestamp.from(r.scheduledAt()),
        hold,
        key,
        hash);
    events.audit(customer.toString(), "PAYMENT_RESERVED", id);
    return get(customer, id);
  }

  @Transactional
  public Map<String, Object> cancel(UUID customer, UUID id) {
    lockCustomer(customer);
    var o = lockOrder(customer, id);
    if ("CANCELLED".equals(o.get("status"))) return o;
    if (!"PENDING".equals(o.get("status")))
      throw error(409, "ALREADY_PROCESSED", "Only an unsettled payment order can be cancelled.");
    release(o);
    db.update("update payment_orders set status='CANCELLED',updated_at=now() where id=?", id);
    events.audit(customer.toString(), "PAYMENT_CANCELLED", id);
    events.event(
        customer,
        id,
        "TRANSFER_CANCELLED",
        "Your pending simulated payment was cancelled and its reservation released.");
    return get(customer, id);
  }

  @Transactional
  public void process(UUID customer, UUID id) {
    lockCustomer(customer);
    var order = lockOrder(customer, id);
    if (!"PENDING".equals(order.get("status"))
        || ((java.sql.Timestamp) order.get("scheduled_at")).toInstant().isAfter(Instant.now()))
      return;
    // A rejection is a committed business state; no debit is ever posted.
    var accounts =
        db.queryForList(
            "select * from bank_accounts where id in (?,?) order by id for update",
            order.get("source_id"),
            order.get("destination_id"));
    boolean active =
        accounts.size() == 2 && accounts.stream().allMatch(a -> "ACTIVE".equals(a.get("status")));
    var verified =
        "VERIFIED"
            .equals(
                db.queryForObject(
                    "select kyc_status from customers where id=?", String.class, customer));
    var recipient = (UUID) order.get("destination_id");
    boolean permitted =
        db.queryForObject(
                "select count(*) from bank_accounts where id=? and (customer_id=? or exists(select"
                    + " 1 from beneficiaries where customer_id=? and account_id=?))",
                Integer.class,
                recipient,
                customer,
                customer,
                recipient)
            > 0;
    var todaySent =
        db.queryForObject(
            "select coalesce(sum(amount),0) from transfers where customer_id=? and reversal_of is"
                + " null and (created_at at time zone 'Asia/Kuala_Lumpur')::date=(now() at time"
                + " zone 'Asia/Kuala_Lumpur')::date",
            BigDecimal.class,
            customer);
    var todayReserved =
        db.queryForObject(
            "select coalesce(sum(amount),0) from payment_orders where customer_id=? and id<>? and"
                + " status='PENDING' and (scheduled_at at time zone"
                + " 'Asia/Kuala_Lumpur')::date=(now() at time zone 'Asia/Kuala_Lumpur')::date",
            BigDecimal.class,
            customer,
            id);
    boolean withinLimit =
        todaySent
                .add(todayReserved)
                .add((BigDecimal) order.get("amount"))
                .compareTo(new BigDecimal("25000.00"))
            <= 0;
    release(order);
    var reference = "MOCK-" + id.toString().replace("-", "");
    if (!active
        || !verified
        || !permitted
        || !withinLimit
        || "REJECT".equals(order.get("expected_outcome"))) {
      var code =
          !active
              ? "ACCOUNT_INACTIVE"
              : !verified
                  ? "KYC_REQUIRED"
                  : !permitted
                      ? "INVALID_BENEFICIARY"
                      : !withinLimit ? "DAILY_LIMIT" : "MOCK_NETWORK_REJECTED";
      db.update(
          "update payment_orders set"
              + " status='REJECTED',failure_code=?,network_reference=?,updated_at=now() where id=?",
          code,
          reference,
          id);
      if ("DUITNOW_MOCK".equals(order.get("channel")))
        db.update(
            "insert into network_messages(order_id,reference,outcome,amount) values"
                + " (?,?,'REJECTED',?)",
            id,
            reference,
            order.get("amount"));
      events.audit(customer.toString(), "PAYMENT_REJECTED", id);
      events.event(
          customer,
          id,
          "TRANSFER_FAILED",
          "Your simulated payment was rejected (" + code + "). Reserved funds were released.");
      return;
    }
    db.update("update payment_orders set status='PROCESSING' where id=?", id);
    var settled =
        transfers.transfer(
            customer,
            id,
            new TransferRequest(
                (UUID) order.get("source_id"),
                recipient,
                (BigDecimal) order.get("amount"),
                order.get("description").toString()));
    db.update(
        "update payment_orders set"
            + " status='SETTLED',transfer_id=?,network_reference=?,updated_at=now() where id=?",
        settled.get("id"),
        reference,
        id);
    if ("DUITNOW_MOCK".equals(order.get("channel")))
      db.update(
          "insert into network_messages(order_id,reference,outcome,transfer_id,amount) values"
              + " (?,?,'ACCEPTED',?,?)",
          id,
          reference,
          settled.get("id"),
          order.get("amount"));
    events.audit(customer.toString(), "PAYMENT_ORDER_SETTLED", id);
  }

  public Map<String, Object> get(UUID customer, UUID id) {
    var rows =
        db.queryForList("select * from payment_orders where customer_id=? and id=?", customer, id);
    if (rows.isEmpty()) throw error(404, "ORDER_NOT_FOUND", "Payment order not found.");
    return rows.getFirst();
  }

  private void lockCustomer(UUID customer) {
    db.queryForObject("select id from customers where id=? for update", UUID.class, customer);
  }

  private Map<String, Object> lockOrder(UUID customer, UUID id) {
    var rows =
        db.queryForList(
            "select * from payment_orders where customer_id=? and id=? for update", customer, id);
    if (rows.isEmpty()) throw error(404, "ORDER_NOT_FOUND", "Payment order not found.");
    return rows.getFirst();
  }

  private void release(Map<String, Object> order) {
    db.update(
        "update account_holds set released_at=now() where id=? and released_at is null",
        order.get("hold_id"));
    db.update(
        "update bank_accounts set held=held-?,version=version+1 where id=?",
        order.get("amount"),
        order.get("source_id"));
  }

  private String fingerprint(OrderRequest r) {
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
                              + r.description()
                              + "|"
                              + r.channel()
                              + "|"
                              + r.alias()
                              + "|"
                              + r.scheduledAt())
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private BankingException error(int status, String code, String message) {
    return new BankingException(status, code, message);
  }
}
