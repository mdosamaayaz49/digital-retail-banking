package com.nusantara.banking.security;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenClaimsTest {
  private Jwt.Builder valid() {
    return Jwt.withTokenValue("test-only")
        .header("alg", "RS256")
        .subject("fictional")
        .expiresAt(Instant.now().plusSeconds(300))
        .audience(List.of("banking-api"));
  }

  @Test
  void acceptsRequiredClaims() {
    assertFalse(SecurityConfig.requiredClaims(valid().build()).hasErrors());
  }

  @Test
  void rejectsMissingAudienceWithoutServerError() {
    assertTrue(
        SecurityConfig.requiredClaims(valid().claims(c -> c.remove("aud")).build()).hasErrors());
  }

  @Test
  void rejectsWrongAudience() {
    assertTrue(
        SecurityConfig.requiredClaims(valid().audience(List.of("another-api")).build())
            .hasErrors());
  }

  @Test
  void rejectsMissingExpiry() {
    assertTrue(
        SecurityConfig.requiredClaims(valid().claims(c -> c.remove("exp")).build()).hasErrors());
  }

  @Test
  void rejectsBlankSubject() {
    assertTrue(SecurityConfig.requiredClaims(valid().subject(" ").build()).hasErrors());
  }
}
