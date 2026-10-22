/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.domain;

public class ProductAlreadyExistsException extends RuntimeException {

    public ProductAlreadyExistsException(String message) {
        super(message);
    }

    public static ProductAlreadyExistsException forProductCode(String productCode) {
        return new ProductAlreadyExistsException(
                "Inventory for product code '%s' already exists".formatted(productCode));
    }
}
