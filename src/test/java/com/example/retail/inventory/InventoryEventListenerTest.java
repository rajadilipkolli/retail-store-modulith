/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.mockito.Mockito.verify;

import com.example.retail.inventory.internal.application.InventoryEventListener;
import com.example.retail.inventory.internal.application.InventoryService;
import com.example.retail.shared.catalog.ProductCreatedEvent;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryEventListenerTest {

    @Mock private InventoryService inventoryService;

    @InjectMocks private InventoryEventListener inventoryEventListener;

    @Test
    @DisplayName(
            "InventoryEventListener initializes inventory when ProductCreatedEvent is received")
    void onProductCreated_InitializesInventory() {
        ProductCreatedEvent event =
                new ProductCreatedEvent(
                        "PROD_AUTO_001",
                        "New Auto Product",
                        "Auto desc",
                        BigDecimal.valueOf(199.99),
                        "img.jpg");

        inventoryEventListener.onProductCreated(event);

        verify(inventoryService).initializeProductInventory("PROD_AUTO_001");
    }
}
