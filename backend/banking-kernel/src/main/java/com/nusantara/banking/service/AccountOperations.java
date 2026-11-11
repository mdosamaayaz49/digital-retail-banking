package com.nusantara.banking.service;

import com.nusantara.banking.web.BankingException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountOperations {
  private final JdbcTemplate db;
  private final EventService events;

  public AccountOperations(JdbcTemplate db, EventService events) {
    this.db = db;
    this.events = events;
  }

  @Transactional
  public Map<String, Object> open(UUID customer, String type, String name) {
    String kyc =
        db.queryForObject(
            "select kyc_status from customers where id=? for update", String.class, customer);
    if (!"VERIFIED".equals(kyc))
      throw new BankingException(
          403, "KYC_REQUIRED", "Simulated KYC verification is required to open an account.");
    if (!List.of("SAVINGS", "CURRENT").contains(type)
        || name == null
        || name.isBlank()
        || name.length() > 100)
      throw new BankingException(
          400,
          "INVALID_ACCOUNT",
          "Choose a savings or current account and a name of at most 100 characters.");
    if (db.queryForObject(
            "select count(*) from bank_accounts where customer_id=? and status<>'CLOSED'",
            Integer.class,
            customer)
        >= 10)
      throw new BankingException(
          422, "ACCOUNT_LIMIT", "A fictional customer can have at most ten open accounts.");
    var id = UUID.randomUUID();
    var number = "SIM-" + id.toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    db.update(
        "insert into bank_accounts(id,customer_id,account_number,name,type) values (?,?,?,?,?)",
        id,
        customer,
        number,
        name.trim(),
        type);
    events.audit(customer.toString(), "ACCOUNT_OPENED", id);
    events.event(
        customer,
        id,
        "ACCOUNT_OPENED",
        "Your new fictional " + type.toLowerCase(Locale.ROOT) + " account is ready.");
    return db.queryForMap("select * from bank_accounts where id=?", id);
  }

  @Transactional
  public void close(UUID customer, UUID account) {
    db.queryForObject("select id from customers where id=? for update", UUID.class, customer);
    var row = owned(customer, account);
    if ("CLOSED".equals(row.get("status"))) return;
    if (!"ACTIVE".equals(row.get("status")))
      throw new BankingException(422, "ACCOUNT_INACTIVE", "Only an active account can be closed.");
    if (((BigDecimal) row.get("balance")).signum() != 0
        || ((BigDecimal) row.get("held")).signum() != 0)
      throw new BankingException(
          422,
          "ACCOUNT_NOT_EMPTY",
          "Move all funds and release all holds before closing this account.");
    db.update("update bank_accounts set status='CLOSED',version=version+1 where id=?", account);
    events.audit(customer.toString(), "ACCOUNT_CLOSED", account);
    events.event(
        customer,
        account,
        "ACCOUNT_CLOSED",
        "Your fictional account was closed. Its history is retained.");
  }

  public Map<String, Object> owned(UUID customer, UUID account) {
    var rows =
        db.queryForList(
            "select * from bank_accounts where id=? and customer_id=? for update",
            account,
            customer);
    if (rows.isEmpty()) throw new BankingException(404, "ACCOUNT_NOT_FOUND", "Account not found.");
    return rows.getFirst();
  }

  @Transactional
  public Map<String, Object> hold(
      UUID account, UUID key, BigDecimal amount, String reason, String actor) {
    var customers =
        db.queryForList(
            "select customer_id from bank_accounts where id=? and customer_id is not null",
            account);
    if (customers.isEmpty())
      throw new BankingException(404, "ACCOUNT_NOT_FOUND", "Account not found.");
    var customer = (UUID) customers.getFirst().get("customer_id");
    db.queryForObject("select id from customers where id=? for update", UUID.class, customer);
    var existing =
        db.queryForList(
            "select * from account_holds where customer_id=? and idempotency_key=?", customer, key);
    if (!existing.isEmpty()) {
      var h = existing.getFirst();
      if (!account.equals(h.get("account_id"))
          || amount.compareTo((BigDecimal) h.get("amount")) != 0
          || !reason.equals(h.get("reason")))
        throw new BankingException(
            409, "IDEMPOTENCY_CONFLICT", "This key was used for another hold.");
      return h;
    }
    var a = owned(customer, account);
    if (!"ACTIVE".equals(a.get("status")))
      throw new BankingException(422, "ACCOUNT_INACTIVE", "The account must be active.");
    if (amount == null
        || amount.signum() <= 0
        || amount.scale() > 2
        || ((BigDecimal) a.get("balance")).subtract((BigDecimal) a.get("held")).compareTo(amount)
            < 0)
      throw new BankingException(
          422, "INSUFFICIENT_FUNDS", "The hold exceeds available funds or has an invalid amount.");
    var id = UUID.randomUUID();
    db.update(
        "insert into account_holds(id,account_id,amount,reason,customer_id,idempotency_key) values"
            + " (?,?,?,?,?,?)",
        id,
        account,
        amount,
        reason,
        customer,
        key);
    db.update("update bank_accounts set held=held+?,version=version+1 where id=?", amount, account);
    events.audit(actor, "HOLD_PLACED", id);
    return db.queryForMap("select * from account_holds where id=?", id);
  }

  @Transactional
  public void release(UUID id, String actor) {
    var lookup = db.queryForList("select * from account_holds where id=?", id);
    if (lookup.isEmpty()) throw new BankingException(404, "HOLD_NOT_FOUND", "Hold not found.");
    var customer = (UUID) lookup.getFirst().get("customer_id");
    db.queryForObject("select id from customers where id=? for update", UUID.class, customer);
    if (db.queryForObject("select count(*) from payment_orders where hold_id=?", Integer.class, id)
        > 0)
      throw new BankingException(
          409, "ORDER_HOLD", "Cancel or settle the payment order to release its hold.");
    var account = (UUID) lookup.getFirst().get("account_id");
    owned(customer, account);
    var h = db.queryForMap("select * from account_holds where id=? for update", id);
    if (h.get("released_at") != null) return;
    db.update("update account_holds set released_at=now() where id=?", id);
    db.update(
        "update bank_accounts set held=held-?,version=version+1 where id=?",
        h.get("amount"),
        account);
    events.audit(actor, "HOLD_RELEASED", id);
  }
}
