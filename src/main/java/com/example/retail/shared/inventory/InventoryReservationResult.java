/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.inventory;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record InventoryReservationResult(
        String idempotencyKey,
        Long orderId,
        InventoryReservationStatus status,
        String message,
        List<ReservationItem> items)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
