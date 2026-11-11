package com.nusantara.banking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
public class Account {
  @Id private UUID id;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "account_number")
  private String accountNumber;

  private String name;
  private String type;
  private String status;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(length = 3)
  private String currency;

  @Column(precision = 19, scale = 2)
  private BigDecimal balance;

  @Column(precision = 19, scale = 2)
  private BigDecimal held;

  @Version private long version;

  protected Account() {}

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public String getStatus() {
    return status;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public BigDecimal getHeld() {
    return held;
  }

  public BigDecimal getAvailableBalance() {
    return balance.subtract(held);
  }
}
