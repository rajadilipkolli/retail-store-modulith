/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.application;

import com.example.retail.shared.catalog.ProductCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryEventListener.class);

    private final InventoryService inventoryService;

    public InventoryEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Initializes zero inventory for the created product, preserving an existing inventory record.
     */
    @ApplicationModuleListener
    public void onProductCreated(ProductCreatedEvent event) {
        LOGGER.info(
                "Received ProductCreatedEvent for product: {}, initializing stock",
                event.productCode());
        inventoryService.initializeProductInventory(event.productCode());
    }
}
