/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.inventory;

import java.io.Serial;
import java.io.Serializable;

public record InventoryResponse(
        Long id, String productCode, Integer availableQuantity, Integer reservedItems)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
