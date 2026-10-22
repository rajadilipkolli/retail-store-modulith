/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.domain;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String idempotencyKey) {
        super("Payment with key '%s' not found".formatted(idempotencyKey));
    }
}
