package com.nusantara.banking.service;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class BankingMetrics {
  @Bean
  MeterBinder businessMetrics(JdbcTemplate db) {
    return registry -> {
      Gauge.builder(
              "banking.outbox.pending",
              db,
              d ->
                  d.queryForObject(
                      "select count(*) from outbox_events where published_at is null",
                      Double.class))
          .register(registry);
      Gauge.builder(
              "banking.payments.pending",
              db,
              d ->
                  d.queryForObject(
                      "select count(*) from payment_orders where status='PENDING'", Double.class))
          .register(registry);
      Gauge.builder(
              "banking.notifications.quarantined",
              db,
              d ->
                  d.queryForObject(
                      "select count(*) from notification_dead_letters where replayed_at is null",
                      Double.class))
          .register(registry);
    };
  }
}
