package com.nusantara.auth;

import com.nusantara.banking.security.*;
import com.nusantara.banking.web.Errors;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
    exclude = {
      org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
      org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
      org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
    })
@Import({SecurityConfig.class, OpenApiConfig.class, Errors.class})
public class AuthApplication {
  public static void main(String[] args) {
    SpringApplication.run(AuthApplication.class, args);
  }
}
