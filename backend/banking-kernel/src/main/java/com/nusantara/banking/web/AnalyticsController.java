package com.nusantara.banking.web;

import com.nusantara.banking.security.CurrentCustomer;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@Validated
public class AnalyticsController {
  private final JdbcTemplate db;
  private final CurrentCustomer current;

  public AnalyticsController(JdbcTemplate db, CurrentCustomer current) {
    this.db = db;
    this.current = current;
  }

  public record Point(String name, String period, BigDecimal income, BigDecimal spending) {}

  @GetMapping
  public List<Point> chart(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "6") @Min(1) @Max(12) int months) {
    var customer = current.id(jwt);
    var result = new ArrayList<Point>();
    var month = YearMonth.now(ZoneId.of("Asia/Kuala_Lumpur"));
    for (int i = months - 1; i >= 0; i--) {
      var m = month.minusMonths(i);
      var from = m.atDay(1);
      var to = m.plusMonths(1).atDay(1);
      var values =
          db.queryForMap(
              "select coalesce(sum(case when l.amount>0 then l.amount else 0 end),0) as"
                  + " income,coalesce(sum(case when l.amount<0 then -l.amount else 0 end),0) as"
                  + " spending from ledger_entries l join bank_accounts a on a.id=l.account_id join"
                  + " transfers t on t.id=l.transfer_id join bank_accounts src on"
                  + " src.id=t.source_id join bank_accounts dst on dst.id=t.destination_id where"
                  + " a.customer_id=? and l.created_at>=?::date::timestamp at time zone"
                  + " 'Asia/Kuala_Lumpur' and l.created_at<?::date::timestamp at time zone"
                  + " 'Asia/Kuala_Lumpur' and src.customer_id is distinct from dst.customer_id",
              customer,
              from.toString(),
              to.toString());
      result.add(
          new Point(
              m.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)),
              m.toString(),
              (BigDecimal) values.get("income"),
              (BigDecimal) values.get("spending")));
    }
    return result;
  }
}
