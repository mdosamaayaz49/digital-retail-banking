package com.nusantara.notifications;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NotificationPersistenceTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

  @Test
  void replayedEventProducesOneNotification() throws Exception {
    var db =
        new JdbcTemplate(
            new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    db.execute(
        "create table notification_records(event_id uuid primary key,customer_id uuid not"
            + " null,event_type varchar(60),message text,created_at timestamptz default now())");
    var consumer = new NotificationConsumer(db, new ObjectMapper());
    var payload =
        "{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"customerId\":\"00000000-0000-0000-0000-000000000001\",\"type\":\"TRANSFER_COMPLETED\",\"message\":\"Simulated"
            + " funds settled\"}";
    consumer.receive(payload);
    consumer.receive(payload);
    assertEquals(1, db.queryForObject("select count(*) from notification_records", Integer.class));
    assertEquals(
        "Simulated funds settled",
        db.queryForObject("select message from notification_records", String.class));
    assertThrows(Exception.class, () -> consumer.receive("malformed event"));
  }
}
