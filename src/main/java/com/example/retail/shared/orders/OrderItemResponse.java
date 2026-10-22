/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.orders;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record OrderItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long itemId,
        String productId,
        int quantity,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal productPrice,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00") BigDecimal price)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
