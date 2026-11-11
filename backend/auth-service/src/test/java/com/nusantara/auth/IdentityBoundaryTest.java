package com.nusantara.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityBoundaryTest {
  @Autowired MockMvc mvc;

  @Test
  void discoveryIsPublic() throws Exception {
    mvc.perform(get("/api/v1/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pkce").value("S256"));
  }

  @Test
  void identityIsPrivate() throws Exception {
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedIdentityDoesNotExposeTokens() throws Exception {
    mvc.perform(
            get("/api/v1/auth/me")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("fictional")
                                    .claim("preferred_username", "amira")
                                    .expiresAt(java.time.Instant.now().plusSeconds(300)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("fictional"))
        .andExpect(jsonPath("$.access_token").doesNotExist());
  }

  @Test
  void foreignDomainRoutesAreNotExposed() throws Exception {
    mvc.perform(get("/api/v1/customers/me").with(jwt())).andExpect(status().isNotFound());
  }
}
