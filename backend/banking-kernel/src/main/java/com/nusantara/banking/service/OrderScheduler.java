package com.nusantara.banking.service;

import java.util.UUID;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "banking.orders-enabled", havingValue = "true", matchIfMissing = true)
public class OrderScheduler {
  private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);
  private final JdbcTemplate db;
  private final PaymentOrderService orders;

  public OrderScheduler(JdbcTemplate db, PaymentOrderService orders) {
    this.db = db;
    this.orders = orders;
  }

  @Scheduled(fixedDelay = 3000)
  public void settle() {
    for (var row :
        db.queryForList(
            "select id,customer_id from payment_orders where status='PENDING' and"
                + " scheduled_at<=now() order by scheduled_at limit 25")) {
      try {
        orders.process((UUID) row.get("customer_id"), (UUID) row.get("id"));
      } catch (Exception e) {
        log.error(
            "Payment order {} could not settle; transaction rolled back and retained for retry",
            row.get("id"),
            e);
      }
    }
  }
}
