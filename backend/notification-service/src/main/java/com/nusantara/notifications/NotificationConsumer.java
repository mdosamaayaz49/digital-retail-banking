package com.nusantara.notifications;

import com.fasterxml.jackson.databind.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationConsumer {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public NotificationConsumer(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @KafkaListener(topics = "banking.events", groupId = "notifications-v1")
  @Transactional
  public void receive(String payload) throws Exception {
    var e = json.readTree(payload);
    db.update(
        "insert into notification_records(event_id,customer_id,event_type,message) values (?,?,?,?)"
            + " on conflict(event_id) do nothing",
        UUID.fromString(e.get("eventId").asText()),
        UUID.fromString(e.get("customerId").asText()),
        e.get("type").asText(),
        e.get("message").asText());
  }
}
