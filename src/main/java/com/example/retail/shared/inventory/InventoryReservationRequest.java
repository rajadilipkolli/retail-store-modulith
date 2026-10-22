/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record InventoryReservationRequest(
        @NotBlank(message = "Idempotency key cannot be blank") String idempotencyKey,
        @NotNull(message = "Order id cannot be null") Long orderId,
        @NotEmpty(message = "Items list cannot be empty")
                List<@NotNull @Valid ReservationItem> items)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
