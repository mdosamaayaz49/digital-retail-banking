package com.nusantara.banking.security;

import com.nusantara.banking.web.BankingException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentCustomer {
  private final JdbcTemplate db;

  public CurrentCustomer(JdbcTemplate db) {
    this.db = db;
  }

  public UUID id(Jwt jwt) {
    var ids =
        db.query(
            "select id from customers where subject=?",
            (rs, n) -> rs.getObject(1, UUID.class),
            jwt.getSubject());
    if (ids.isEmpty())
      throw new BankingException(
          403, "PROFILE_REQUIRED", "Your fictional customer profile is not provisioned.");
    return ids.getFirst();
  }
}
