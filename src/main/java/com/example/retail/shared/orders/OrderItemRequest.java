/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.orders;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank(message = "Product code must be provided") String productCode,
        @Positive(message = "Quantity should be greater than zero") int quantity,
        @NotNull
                @DecimalMin(
                        value = "0.01",
                        inclusive = true,
                        message = "Price should be greater than zero")
                @Digits(integer = 17, fraction = 2)
                BigDecimal productPrice)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
