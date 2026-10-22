/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.domain;

public class PaymentIdempotencyConflictException extends RuntimeException {

    public PaymentIdempotencyConflictException(String key) {
        super(
                "Payment idempotency conflict: key '%s' was already used with different operation parameters"
                        .formatted(key));
    }

    public static PaymentIdempotencyConflictException forKey(String key) {
        return new PaymentIdempotencyConflictException(key);
    }
}
