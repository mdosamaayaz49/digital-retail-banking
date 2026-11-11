package com.nusantara.account;

import com.nusantara.banking.security.*;
import com.nusantara.banking.service.*;
import com.nusantara.banking.web.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;

@SpringBootApplication
@org.springframework.boot.autoconfigure.domain.EntityScan("com.nusantara.banking.domain")
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(
    "com.nusantara.banking.repository")
@Import({
  AccountController.class,
  AccountLifecycleController.class,
  AccountOperations.class,
  CurrentCustomer.class,
  EventService.class,
  SecurityConfig.class,
  OpenApiConfig.class,
  Errors.class
})
public class AccountApplication {
  public static void main(String[] args) {
    SpringApplication.run(AccountApplication.class, args);
  }
}
