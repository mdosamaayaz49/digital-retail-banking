package com.nusantara.banking.web;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportingController {
  private final JdbcTemplate db;

  public ReportingController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping
  public Map<String, Object> report(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    if (from.isAfter(to) || from.plusYears(1).isBefore(to))
      throw new BankingException(
          422, "INVALID_RANGE", "Choose a reporting period of at most one year.");
    var summary =
        db.queryForMap(
            "select count(*) filter(where reversal_of is null) as payments,coalesce(sum(amount)"
                + " filter(where reversal_of is null),0) as gross_amount,coalesce(sum(amount)"
                + " filter(where reversal_of is not null),0) as compensation_amount,count(*)"
                + " filter(where status='REVERSED') as reversed_payments from transfers where"
                + " customer_id is not null and created_at>=?::date::timestamp at time zone"
                + " 'Asia/Kuala_Lumpur' and created_at<(?::date+1)::timestamp at time zone"
                + " 'Asia/Kuala_Lumpur'",
            from.toString(),
            to.toString());
    var channels =
        db.queryForList(
            "select channel,status,count(*) as orders,coalesce(sum(amount),0) as amount from"
                + " payment_orders where created_at>=?::date::timestamp at time zone"
                + " 'Asia/Kuala_Lumpur' and created_at<(?::date+1)::timestamp at time zone"
                + " 'Asia/Kuala_Lumpur' group by channel,status order by channel,status",
            from.toString(),
            to.toString());
    return Map.of(
        "from",
        from,
        "to",
        to,
        "currency",
        "MYR",
        "summary",
        summary,
        "channels",
        channels,
        "note",
        "Gross amounts include own-account transfers; compensations are reported separately."
            + " Fictional funds only.");
  }
}
