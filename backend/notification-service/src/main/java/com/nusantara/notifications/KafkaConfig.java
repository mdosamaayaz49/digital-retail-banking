package com.nusantara.notifications;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConfig {
  @Bean
  DefaultErrorHandler errors(JdbcTemplate db) {
    var backoff = new ExponentialBackOffWithMaxRetries(5);
    backoff.setInitialInterval(500);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(5000);
    var handler =
        new DefaultErrorHandler(
            (record, error) -> {
              db.update(
                  "insert into"
                      + " notification_dead_letters(topic,partition_id,offset_id,payload,error_type)"
                      + " values (?,?,?,?,?) on conflict(topic,partition_id,offset_id) do nothing",
                  record.topic(),
                  record.partition(),
                  record.offset(),
                  String.valueOf(record.value()),
                  error.getClass().getSimpleName());
            },
            backoff);
    // A failed durable recovery insert throws: the offset remains uncommitted and the record is
    // retried.
    handler.setAckAfterHandle(true);
    return handler;
  }
}
