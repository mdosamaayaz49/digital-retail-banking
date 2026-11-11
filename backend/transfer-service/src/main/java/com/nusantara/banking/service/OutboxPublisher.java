package com.nusantara.banking.service;

import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "banking.outbox-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;

  public OutboxPublisher(JdbcTemplate db, KafkaTemplate<String, String> kafka) {
    this.db = db;
    this.kafka = kafka;
  }

  @Scheduled(fixedDelay = 2000)
  @Transactional
  public void publish() {
    for (var event :
        db.queryForList(
            "select * from outbox_events where published_at is null order by created_at limit 20"
                + " for update skip locked")) {
      try {
        kafka
            .send(
                "banking.events",
                event.get("aggregate_id").toString(),
                event.get("payload").toString())
            .get(12, TimeUnit.SECONDS);
        db.update(
            "update outbox_events set published_at=now(),attempts=attempts+1 where id=?",
            event.get("id"));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Outbox worker interrupted", e);
      } catch (Exception e) {
        db.update("update outbox_events set attempts=attempts+1 where id=?", event.get("id"));
        log.warn("Outbox delivery failed for event {}; retained for retry", event.get("id"));
        break;
      }
    }
  }
}
