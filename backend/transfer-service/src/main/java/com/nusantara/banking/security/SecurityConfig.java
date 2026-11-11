package com.nusantara.banking.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          var realm = jwt.getClaimAsMap("realm_access");
          var roles =
              realm == null ? List.of() : ((List<?>) realm.getOrDefault("roles", List.of()));
          return roles.stream()
              .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
              .map(a -> (org.springframework.security.core.GrantedAuthority) a)
              .toList();
        });
    return http.cors(c -> {})
        .csrf(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(t -> t.jwtAuthenticationConverter(converter)))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            jwt ->
                jwt.getAudience().contains("banking-api")
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Invalid audience", null))));
    return decoder;
  }

  @Bean
  CorsConfigurationSource cors(@Value("${banking.cors-origin}") String origin) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(List.of(origin));
    c.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    c.setExposedHeaders(List.of("Content-Disposition"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
