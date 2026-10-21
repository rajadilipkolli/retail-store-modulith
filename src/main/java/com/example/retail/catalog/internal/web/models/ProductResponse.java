/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog.internal.web.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;

public record ProductResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        String productCode,
        String productName,
        String description,
        String imageUrl,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00") BigDecimal price,
        boolean inStock)
        implements Serializable {

    public ProductResponse withInStock(final boolean inStock) {
        return this.inStock == inStock
                ? this
                : new ProductResponse(
                        this.id,
                        this.productCode,
                        this.productName,
                        this.description,
                        this.imageUrl,
                        this.price,
                        inStock);
    }
}
