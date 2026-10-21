/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.catalog;

import java.io.Serializable;
import java.math.BigDecimal;
import org.springframework.modulith.events.Externalized;

@Externalized("catalog.exchange::product.created")
public record ProductCreatedEvent(
        String productCode,
        String productName,
        String description,
        BigDecimal price,
        String imageUrl)
        implements Serializable {}
