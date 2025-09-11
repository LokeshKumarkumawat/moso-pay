package com.bytebyteboot.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record  PaymentOrderRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        String currency,

        @NotBlank(message = "Receipt is required")
        String receipt,

        @Email(message = "Valid email is required")
        String customerEmail,

        @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Valid phone number is required")
        String customerPhone,

        String notes
) {}