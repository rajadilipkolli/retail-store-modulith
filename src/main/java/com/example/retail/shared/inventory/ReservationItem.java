/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;

public record ReservationItem(
        @NotBlank(message = "Product code cannot be blank") String productCode,
        @NotNull(message = "Quantity cannot be null")
                @Positive(message = "Quantity must be positive")
                Integer quantity)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
