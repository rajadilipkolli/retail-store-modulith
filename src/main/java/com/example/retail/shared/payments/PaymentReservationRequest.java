/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.payments;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record PaymentReservationRequest(
        @NotBlank(message = "Idempotency key is required") String idempotencyKey,
        @NotNull(message = "Order ID is required") Long orderId,
        @NotNull(message = "Customer ID is required") Long customerId,
        @NotNull(message = "Amount is required")
                @DecimalMin(value = "0.01", message = "Amount must be strictly positive")
                BigDecimal amount,
        String currency)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** Creates a reservation request denominated in USD. */
    public PaymentReservationRequest(
            String idempotencyKey, Long orderId, Long customerId, BigDecimal amount) {
        this(idempotencyKey, orderId, customerId, amount, "USD");
    }
}
