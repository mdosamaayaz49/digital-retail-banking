package com.nusantara.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nusantara.banking.dto.PageResult;
import com.nusantara.banking.web.BankingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notification-dead-letters")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class NotificationRecoveryController {
  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;

  public NotificationRecoveryController(
      JdbcTemplate db, KafkaTemplate<String, String> kafka, ObjectMapper json) {
    this.db = db;
    this.kafka = kafka;
    this.json = json;
  }

  public record Repair(
      @NotNull UUID customerId,
      @NotBlank @Pattern(regexp = "[A-Z_]{3,60}") String eventType,
      @NotBlank @Size(max = 500) String message) {}

  @GetMapping
  public PageResult<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return new PageResult<>(
        db.queryForList(
            "select id,topic,partition_id,offset_id,error_type,created_at,replayed_at from"
                + " notification_dead_letters order by id desc limit ? offset ?",
            size,
            (long) page * size),
        page,
        size,
        db.queryForObject("select count(*) from notification_dead_letters", Long.class));
  }

  @PostMapping("/{id}/retry")
  @Transactional
  public void repair(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody Repair r)
      throws Exception {
    var rows = db.queryForList("select * from notification_dead_letters where id=? for update", id);
    if (rows.isEmpty())
      throw new BankingException(
          404, "RECORD_NOT_FOUND", "Notification recovery record not found.");
    if (rows.getFirst().get("replayed_at") != null) return;
    if (db.queryForObject(
            "select count(*) from customers where id=?", Integer.class, r.customerId())
        == 0)
      throw new BankingException(
          422, "CUSTOMER_NOT_FOUND", "Select an existing fictional customer.");
    var eventId =
        UUID.nameUUIDFromBytes(("notification-repair:" + id).getBytes(StandardCharsets.UTF_8));
    var payload =
        json.writeValueAsString(
            Map.of(
                "eventId",
                eventId,
                "customerId",
                r.customerId(),
                "type",
                r.eventType(),
                "message",
                r.message()));
    kafka.send("banking.events", r.customerId().toString(), payload).get(12, TimeUnit.SECONDS);
    db.update(
        "update notification_dead_letters set replayed_at=now(),repaired_payload=? where id=?",
        payload,
        id);
    db.update(
        "insert into audit_logs(actor,action,resource) values (?,'NOTIFICATION_REPAIRED',?)",
        jwt.getSubject(),
        Long.toString(id));
  }
}
