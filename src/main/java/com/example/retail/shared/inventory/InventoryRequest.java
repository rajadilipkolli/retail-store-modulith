/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

public record InventoryRequest(
        @NotBlank(message = "Product code cannot be blank") String productCode,
        @NotNull(message = "Available quantity cannot be null")
                @Min(value = 0, message = "Available quantity must be non-negative")
                Integer availableQuantity)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
