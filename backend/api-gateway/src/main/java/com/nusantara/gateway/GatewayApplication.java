package com.nusantara.gateway;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class GatewayApplication {
  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }

  @Bean
  KeyResolver clientKey() {
    return exchange ->
        Mono.just(
            exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
  }
}
