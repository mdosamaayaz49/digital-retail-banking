package com.nusantara.banking.web;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
public class ReconciliationController {
  private final JdbcTemplate db;

  public ReconciliationController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping
  public Map<String, Object> report() {
    var balances =
        db.queryForList(
            "select a.id,a.balance,coalesce(sum(l.amount),0) as ledger_balance from bank_accounts a"
                + " left join ledger_entries l on l.account_id=a.id group by a.id having"
                + " a.balance<>coalesce(sum(l.amount),0)");
    var ledger =
        db.queryForList(
            "select transfer_id,count(*) as postings,sum(amount) as net from ledger_entries group"
                + " by transfer_id having count(*)<>2 or sum(amount)<>0");
    var network =
        db.queryForList(
            "select o.id,o.status,n.reference,n.outcome from payment_orders o left join"
                + " network_messages n on n.order_id=o.id where o.channel='DUITNOW_MOCK' and"
                + " o.status in ('SETTLED','REJECTED') and (n.order_id is null or"
                + " (o.status='SETTLED' and (n.outcome<>'ACCEPTED' or n.transfer_id is distinct"
                + " from o.transfer_id or n.amount<>o.amount)) or (o.status='REJECTED' and"
                + " n.outcome<>'REJECTED'))");
    var holds =
        db.queryForList(
            "select a.id,a.held,coalesce(sum(h.amount),0) as holds_total from bank_accounts a left"
                + " join account_holds h on h.account_id=a.id and h.released_at is null group by"
                + " a.id having a.held<>coalesce(sum(h.amount),0)");
    return Map.of(
        "balanced",
        balances.isEmpty() && ledger.isEmpty() && network.isEmpty() && holds.isEmpty(),
        "balanceDifferences",
        balances,
        "ledgerDifferences",
        ledger,
        "networkDifferences",
        network,
        "holdDifferences",
        holds);
  }
}
