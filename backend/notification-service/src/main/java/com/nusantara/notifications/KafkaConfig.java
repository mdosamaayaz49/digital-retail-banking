package com.nusantara.notifications;

import org.springframework.context.annotation.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
  @Bean
  DefaultErrorHandler errors() {
    var handler = new DefaultErrorHandler(new FixedBackOff(1000L, Long.MAX_VALUE));
    handler.setCommitRecovered(false);
    return handler;
  }
}
