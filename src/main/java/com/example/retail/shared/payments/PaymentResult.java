/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.payments;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record PaymentResult(
        String idempotencyKey,
        Long orderId,
        Long customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String message)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
