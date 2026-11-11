package com.nusantara.statement;

import com.nusantara.banking.security.*;
import com.nusantara.banking.service.*;
import com.nusantara.banking.web.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;

@SpringBootApplication(
    exclude = {
      org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
      org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
    })
@Import({
  StatementController.class,
  StatementService.class,
  CurrentCustomer.class,
  EventService.class,
  SecurityConfig.class,
  OpenApiConfig.class,
  Errors.class
})
public class StatementApplication {
  public static void main(String[] args) {
    SpringApplication.run(StatementApplication.class, args);
  }
}
