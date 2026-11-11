package com.nusantara.banking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nusantara.banking.service.OutboxPublisher;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {
  @SuppressWarnings("unchecked")
  @Test
  void publishSuccessMarksAcknowledgedEvent() {
    var db = mock(JdbcTemplate.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var id = UUID.randomUUID();
    when(db.queryForList(anyString()))
        .thenReturn(List.of(Map.of("id", id, "aggregate_id", id, "payload", "{}")));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    new OutboxPublisher(db, kafka).publish();
    verify(db).update(contains("published_at=now()"), eq(id));
  }

  @SuppressWarnings("unchecked")
  @Test
  void failureRetainsEventForRetry() {
    var db = mock(JdbcTemplate.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var id = UUID.randomUUID();
    when(db.queryForList(anyString()))
        .thenReturn(List.of(Map.of("id", id, "aggregate_id", id, "payload", "{}")));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker offline")));
    new OutboxPublisher(db, kafka).publish();
    verify(db).update("update outbox_events set attempts=attempts+1 where id=?", id);
    verify(db, never()).update(contains("published_at=now()"), eq(id));
  }
}
