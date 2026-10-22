/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.payments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serial;
import java.io.Serializable;
import org.springframework.modulith.events.Externalized;

@Externalized("payment.exchange::payment.processed")
public record PaymentProcessedEvent(@JsonValue PaymentResult result) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @JsonCreator
    public PaymentProcessedEvent(PaymentResult result) {
        this.result = result;
    }
}
