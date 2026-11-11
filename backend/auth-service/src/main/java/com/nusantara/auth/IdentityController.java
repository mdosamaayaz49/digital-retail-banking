package com.nusantara.auth;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentityController {
  private final String issuer;

  public IdentityController(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
    this.issuer = issuer;
  }

  @GetMapping("/config")
  public Map<String, Object> config() {
    return Map.of(
        "issuer",
        issuer,
        "clientId",
        "banking-web",
        "flow",
        "authorization_code",
        "pkce",
        "S256",
        "accountConsole",
        issuer + "/account/",
        "registration",
        issuer + "/protocol/openid-connect/registrations",
        "discovery",
        issuer + "/.well-known/openid-configuration");
  }

  @GetMapping("/me")
  public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "subject",
        jwt.getSubject(),
        "username",
        Objects.toString(jwt.getClaimAsString("preferred_username"), ""),
        "realmAccess",
        Objects.requireNonNullElse(jwt.getClaimAsMap("realm_access"), Map.of()),
        "expiresAt",
        Objects.requireNonNull(jwt.getExpiresAt()),
        "issuer",
        issuer,
        "accountConsole",
        issuer + "/account/");
  }
}
