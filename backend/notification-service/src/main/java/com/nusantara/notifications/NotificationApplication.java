package com.nusantara.notifications;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

@org.springframework.context.annotation.Import({
  com.nusantara.banking.security.SecurityConfig.class,
  com.nusantara.banking.security.OpenApiConfig.class,
  com.nusantara.banking.web.Errors.class
})
@SpringBootApplication(
    exclude = {
      org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
      org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
      org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
    })
public class NotificationApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotificationApplication.class, args);
  }
}
