package com.nusantara.banking.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
    @NotNull UUID sourceId,
    @NotNull UUID destinationId,
    @NotNull @DecimalMin("0.01") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2)
        BigDecimal amount,
    @NotBlank @Size(max = 140) @Pattern(regexp = "[\\p{L}\\p{N} .,'()/-]+") String description) {}
