package com.nusantara.banking.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderRequest(
    @NotNull UUID sourceId,
    UUID destinationId,
    @NotNull @DecimalMin("0.01") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2)
        BigDecimal amount,
    @NotBlank @Size(max = 140) @Pattern(regexp = "[\\p{L}\\p{N} .,'()/-]+") String description,
    @NotNull @Pattern(regexp = "SCHEDULED|DUITNOW_MOCK") String channel,
    @Size(max = 120) String alias,
    @NotNull Instant scheduledAt) {}
