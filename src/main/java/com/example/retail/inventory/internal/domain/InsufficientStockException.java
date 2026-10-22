/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.domain;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public static InsufficientStockException forProduct(
            String productCode, int requested, int available) {
        return new InsufficientStockException(
                "Insufficient stock for product '%s': requested %d, available %d"
                        .formatted(productCode, requested, available));
    }
}
