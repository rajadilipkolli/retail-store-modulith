/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.domain;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(
            Long customerId, BigDecimal requested, BigDecimal available) {
        super(
                "Customer '%d' has insufficient available balance (requested: %s, available: %s)"
                        .formatted(customerId, requested, available));
    }

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
