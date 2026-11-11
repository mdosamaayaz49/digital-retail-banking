package com.nusantara.banking.repository;

import com.nusantara.banking.domain.Account;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  List<Account> findByCustomerIdOrderByAccountNumber(UUID customerId);

  Optional<Account> findByIdAndCustomerId(UUID id, UUID customerId);
}
