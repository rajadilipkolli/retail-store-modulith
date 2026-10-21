/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog.internal.web.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Catalog response.
 *
 * @param inStock unknown (null) until the Inventory API supplies availability; never inferred from
 *     a request flag
 */
public record ProductResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        String productCode,
        String productName,
        String description,
        String imageUrl,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00") BigDecimal price,
        Boolean inStock)
        implements Serializable {}
