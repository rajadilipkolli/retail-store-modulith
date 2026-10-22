/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.domain;

import com.example.retail.shared.payments.PaymentStatus;

public class InvalidPaymentTransitionException extends RuntimeException {

    public InvalidPaymentTransitionException(PaymentStatus current, String targetAction) {
        super(
                "Cannot perform action '%s' on payment in status '%s'"
                        .formatted(targetAction, current));
    }

    public static InvalidPaymentTransitionException forTransition(
            PaymentStatus current, String targetAction) {
        return new InvalidPaymentTransitionException(current, targetAction);
    }
}
