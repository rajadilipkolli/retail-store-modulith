/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.domain;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(String message) {
        super(message);
    }

    public static InventoryNotFoundException forProductCode(String productCode) {
        return new InventoryNotFoundException(
                "Inventory for product code '%s' was not found".formatted(productCode));
    }

    public static InventoryNotFoundException forId(Long id) {
        return new InventoryNotFoundException("Inventory with id '%d' was not found".formatted(id));
    }
}
