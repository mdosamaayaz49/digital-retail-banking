package com.nusantara.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.backoff.FixedBackOff;

@SpringJUnitConfig(KafkaDeliveryTest.Config.class)
@EmbeddedKafka(partitions = 1, topics = "banking.events", kraft = true)
class KafkaDeliveryTest {
  @Autowired EmbeddedKafkaBroker broker;
  @Autowired JdbcTemplate db;

  @Configuration
  @EnableKafka
  static class Config {
    @Bean
    JdbcTemplate db() {
      return mock(JdbcTemplate.class);
    }

    @Bean
    NotificationConsumer consumer(JdbcTemplate db) {
      return new NotificationConsumer(db, new ObjectMapper());
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
        EmbeddedKafkaBroker broker) {
      var f = new ConcurrentKafkaListenerContainerFactory<String, String>();
      var props = KafkaTestUtils.consumerProps("notifications-v1", "false", broker);
      props.put("auto.offset.reset", "earliest");
      f.setConsumerFactory(
          new DefaultKafkaConsumerFactory<>(
              props,
              new org.apache.kafka.common.serialization.StringDeserializer(),
              new org.apache.kafka.common.serialization.StringDeserializer()));
      f.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(100, 10)));
      return f;
    }
  }

  @Test
  void brokerDeliversAndRetriesTransientDatabaseFailure() throws Exception {
    var attempts = new java.util.concurrent.atomic.AtomicInteger();
    var stored = new CountDownLatch(1);
    doAnswer(
            call -> {
              if (attempts.incrementAndGet() < 3)
                throw new org.springframework.dao.TransientDataAccessResourceException(
                    "injected outage");
              stored.countDown();
              return 1;
            })
        .when(db)
        .update(anyString(), any(Object[].class));
    var props = KafkaTestUtils.producerProps(broker);
    var factory =
        new DefaultKafkaProducerFactory<String, String>(
            props,
            new org.apache.kafka.common.serialization.StringSerializer(),
            new org.apache.kafka.common.serialization.StringSerializer());
    var producer = new KafkaTemplate<>(factory);
    try {
      producer
          .send(
              "banking.events",
              "account",
              "{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"customerId\":\"00000000-0000-0000-0000-000000000001\",\"type\":\"TRANSFER_COMPLETED\",\"message\":\"Simulated"
                  + " funds settled\"}")
          .get(10, TimeUnit.SECONDS);
      assertTrue(stored.await(20, TimeUnit.SECONDS), "Notification should be retried and stored");
      assertEquals(3, attempts.get());
      verify(db, atLeast(3))
          .update(contains("on conflict(event_id) do nothing"), any(Object[].class));
    } finally {
      producer.destroy();
      factory.destroy();
    }
  }
}
