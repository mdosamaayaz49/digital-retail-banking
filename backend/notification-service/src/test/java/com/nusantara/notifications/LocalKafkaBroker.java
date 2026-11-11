package com.nusantara.notifications;

import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/** Development-only real Kafka broker. Not packaged in the production service. */
public final class LocalKafkaBroker {
  public static void main(String[] args) throws Exception {
    var broker = new EmbeddedKafkaKraftBroker(1, 1, "banking.events");

    broker.afterPropertiesSet();
    if (args.length > 0)
      java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), broker.getBrokersAsString());
    Runtime.getRuntime().addShutdownHook(new Thread(broker::destroy));
    System.out.println("Local Kafka ready: " + broker.getBrokersAsString());
    new java.util.concurrent.CountDownLatch(1).await();
  }
}
