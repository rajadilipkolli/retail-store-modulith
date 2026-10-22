/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.orders;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.modulith.events.Externalized;

@Externalized("orders.exchange::order.confirmed")
public record OrderConfirmedEvent(
        Long orderId,
        Long customerId,
        BigDecimal totalPrice,
        String currency,
        LocalDateTime timestamp)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
