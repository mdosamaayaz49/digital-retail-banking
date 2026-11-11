package com.nusantara.banking.service;

import com.nusantara.banking.web.BankingException;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class StatementService {
  private final JdbcTemplate db;
  private final EventService events;

  public StatementService(JdbcTemplate db, EventService events) {
    this.db = db;
    this.events = events;
  }

  @Transactional
  public Map<String, Object> generate(UUID customer, UUID account, LocalDate from, LocalDate to) {
    if (from.isAfter(to)
        || to.isAfter(LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")))
        || from.plusYears(1).isBefore(to))
      throw new BankingException(
          422, "INVALID_RANGE", "Choose a past statement period of at most one year.");
    if (db.queryForObject(
            "select count(*) from bank_accounts where id=? and customer_id=?",
            Integer.class,
            account,
            customer)
        == 0) throw new BankingException(404, "ACCOUNT_NOT_FOUND", "Account not found.");
    var id = UUID.randomUUID();
    db.update(
        "insert into bank_statements(id,customer_id,account_id,date_from,date_to) values"
            + " (?,?,?,?,?)",
        id,
        customer,
        account,
        from,
        to);
    events.audit(customer.toString(), "STATEMENT_GENERATED", id);
    return db.queryForMap("select * from bank_statements where id=?", id);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public byte[] pdf(UUID customer, UUID id) {
    var rows =
        db.queryForList(
            "select s.*,a.account_number,a.name,c.display_name from bank_statements s join"
                + " bank_accounts a on a.id=s.account_id join customers c on c.id=s.customer_id"
                + " where s.id=? and s.customer_id=?",
            id,
            customer);
    if (rows.isEmpty())
      throw new BankingException(404, "STATEMENT_NOT_FOUND", "Statement not found.");
    var s = rows.getFirst();
    var account = s.get("account_id");
    String from = s.get("date_from").toString(), to = s.get("date_to").toString();
    var opening =
        db.queryForObject(
            "select coalesce(sum(amount),0) from ledger_entries where account_id=? and"
                + " created_at<?::date::timestamp at time zone 'Asia/Kuala_Lumpur'",
            BigDecimal.class,
            account,
            from);
    var entries =
        db.queryForList(
            "select l.amount,t.reference,t.description,t.created_at from ledger_entries l join"
                + " transfers t on t.id=l.transfer_id where l.account_id=? and"
                + " l.created_at>=?::date::timestamp at time zone 'Asia/Kuala_Lumpur' and"
                + " l.created_at<(?::date+1)::timestamp at time zone 'Asia/Kuala_Lumpur' order by"
                + " l.created_at,l.id limit 5001",
            account,
            from,
            to);
    if (entries.size() > 5000)
      throw new BankingException(
          422,
          "STATEMENT_TOO_LARGE",
          "Choose a narrower statement period (maximum 5,000 entries per PDF).");
    var credits = BigDecimal.ZERO;
    var debits = BigDecimal.ZERO;
    for (var e : entries) {
      var a = (BigDecimal) e.get("amount");
      if (a.signum() > 0) credits = credits.add(a);
      else debits = debits.subtract(a);
    }
    var lines = new ArrayList<String>();
    lines.add("NUSANTARA | SIMULATED BANK STATEMENT");
    lines.add("Fictional identities and funds. Not a regulated banking document.");
    lines.add("Customer: " + s.get("display_name"));
    lines.add("Account: " + s.get("name") + " / " + s.get("account_number"));
    lines.add("Period: " + from + " to " + to + " (Asia/Kuala_Lumpur)");
    lines.add("Opening MYR " + opening + " | Credits " + credits + " | Debits " + debits);
    lines.add("Closing MYR " + opening.add(credits).subtract(debits));
    lines.add("");
    for (var e : entries) {
      lines.add(
          e.get("created_at").toString().substring(0, 10)
              + "  MYR "
              + e.get("amount")
              + "  "
              + e.get("reference"));
      lines.add("  " + e.get("description"));
    }
    try (var doc = new PDDocument();
        var bytes = new ByteArrayOutputStream()) {
      for (int start = 0; start < lines.size(); start += 45) {
        var page = new PDPage();
        doc.addPage(page);
        try (var content = new PDPageContentStream(doc, page)) {
          content.beginText();
          content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
          content.newLineAtOffset(40, 750);
          content.setLeading(15);
          for (var line : lines.subList(start, Math.min(start + 45, lines.size()))) {
            content.showText(line.replaceAll("[^\\x20-\\x7E]", "?"));
            content.newLine();
          }
          content.endText();
        }
      }
      doc.save(bytes);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Statement PDF generation failed", e);
    }
  }
}
