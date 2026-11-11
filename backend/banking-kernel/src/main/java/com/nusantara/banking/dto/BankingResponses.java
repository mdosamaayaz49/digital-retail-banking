package com.nusantara.banking.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Public API views. Persistence metadata, request fingerprints and reservation decisions stay
 * internal.
 */
public final class BankingResponses {
  private BankingResponses() {}

  private static UUID id(Map<String, Object> row, String key) {
    var v = row.get(key);
    return v == null ? null : v instanceof UUID u ? u : UUID.fromString(v.toString());
  }

  private static String text(Map<String, Object> row, String key) {
    var v = row.get(key);
    return v == null ? null : v.toString();
  }

  private static BigDecimal amount(Map<String, Object> row) {
    return (BigDecimal) row.get("amount");
  }

  private static Instant time(Map<String, Object> row, String key) {
    var v = row.get(key);
    return v == null
        ? null
        : v instanceof java.sql.Timestamp t
            ? t.toInstant()
            : v instanceof OffsetDateTime t ? t.toInstant() : Instant.parse(v.toString());
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Profile(
      UUID id,
      String displayName,
      String email,
      String phone,
      String kycStatus,
      Instant createdAt) {
    public static Profile from(Map<String, Object> r) {
      return new Profile(
          BankingResponses.id(r, "id"),
          text(r, "display_name"),
          text(r, "email"),
          text(r, "phone"),
          text(r, "kyc_status"),
          time(r, "created_at"));
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Transfer(
      UUID id,
      UUID sourceId,
      UUID destinationId,
      BigDecimal amount,
      String currency,
      String reference,
      String description,
      String status,
      UUID reversalOf,
      Instant createdAt) {
    public static Transfer from(Map<String, Object> r) {
      return new Transfer(
          BankingResponses.id(r, "id"),
          BankingResponses.id(r, "source_id"),
          BankingResponses.id(r, "destination_id"),
          BankingResponses.amount(r),
          text(r, "currency"),
          text(r, "reference"),
          text(r, "description"),
          text(r, "status"),
          BankingResponses.id(r, "reversal_of"),
          time(r, "created_at"));
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Beneficiary(
      UUID id, UUID accountId, String nickname, String accountNumber, Instant createdAt) {
    public static Beneficiary from(Map<String, Object> r) {
      return new Beneficiary(
          BankingResponses.id(r, "id"),
          BankingResponses.id(r, "account_id"),
          text(r, "nickname"),
          text(r, "account_number"),
          time(r, "created_at"));
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Statement(
      UUID id,
      UUID accountId,
      String accountName,
      LocalDate dateFrom,
      LocalDate dateTo,
      Instant createdAt) {
    public static Statement from(Map<String, Object> r) {
      return new Statement(
          BankingResponses.id(r, "id"),
          BankingResponses.id(r, "account_id"),
          text(r, "account_name"),
          LocalDate.parse(text(r, "date_from")),
          LocalDate.parse(text(r, "date_to")),
          time(r, "created_at"));
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Order(
      UUID id,
      UUID sourceId,
      UUID destinationId,
      BigDecimal amount,
      String description,
      String channel,
      String alias,
      String status,
      Instant scheduledAt,
      UUID transferId,
      String networkReference,
      String failureCode,
      Instant createdAt) {
    public static Order from(Map<String, Object> r) {
      return new Order(
          BankingResponses.id(r, "id"),
          BankingResponses.id(r, "source_id"),
          BankingResponses.id(r, "destination_id"),
          BankingResponses.amount(r),
          text(r, "description"),
          text(r, "channel"),
          text(r, "alias"),
          text(r, "status"),
          time(r, "scheduled_at"),
          BankingResponses.id(r, "transfer_id"),
          text(r, "network_reference"),
          text(r, "failure_code"),
          time(r, "created_at"));
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Transaction(
      UUID id,
      String reference,
      String description,
      String status,
      Instant createdAt,
      BigDecimal amount,
      String accountName,
      UUID accountId,
      String currency) {
    public static Transaction from(Map<String, Object> r) {
      return new Transaction(
          BankingResponses.id(r, "id"),
          text(r, "reference"),
          text(r, "description"),
          text(r, "status"),
          time(r, "created_at"),
          BankingResponses.amount(r),
          text(r, "account_name"),
          BankingResponses.id(r, "account_id"),
          text(r, "currency"));
    }
  }
}
