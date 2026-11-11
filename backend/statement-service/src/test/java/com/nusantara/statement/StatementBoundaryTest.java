package com.nusantara.statement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nusantara.banking.security.CurrentCustomer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.password=test-only")
@AutoConfigureMockMvc
class StatementBoundaryTest {
  @Autowired MockMvc mvc;
  @MockitoBean JdbcTemplate db;
  @MockitoBean CurrentCustomer current;

  @Test
  void anonymousDomainAccessRejected() throws Exception {
    mvc.perform(get("/api/v1/statements")).andExpect(status().isUnauthorized());
  }

  @Test
  void financialWritesAreNotExposed() throws Exception {
    mvc.perform(get("/api/v1/transfers/11111111-1111-1111-1111-111111111111").with(jwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void readinessIsExposed() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }
}
