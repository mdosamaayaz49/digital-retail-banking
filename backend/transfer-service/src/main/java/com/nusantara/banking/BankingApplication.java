package com.nusantara.banking;

import com.nusantara.banking.web.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(
    basePackages = "com.nusantara.banking",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
              CustomerController.class,
              AccountController.class,
              AccountLifecycleController.class,
              StatementController.class
            }))
public class BankingApplication {
  public static void main(String[] args) {
    SpringApplication.run(BankingApplication.class, args);
  }
}
