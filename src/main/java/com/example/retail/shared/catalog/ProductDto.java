/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.catalog;

import java.io.Serializable;
import java.math.BigDecimal;

public record ProductDto(
        Long id,
        String productCode,
        String productName,
        String description,
        String imageUrl,
        BigDecimal price)
        implements Serializable {}
