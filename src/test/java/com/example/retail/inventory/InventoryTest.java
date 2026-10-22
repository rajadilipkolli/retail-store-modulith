/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.inventory.internal.domain.Inventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    @DisplayName("Inventory getters, setters, and constructors operate correctly")
    void inventoryEntityAccessors() {
        Inventory inv = new Inventory();
        inv.setId(10L);
        inv.setProductCode("P001");
        inv.setAvailableQuantity(100);
        inv.setReservedItems(5);
        inv.setVersion((short) 1);

        assertThat(inv.getId()).isEqualTo(10L);
        assertThat(inv.getProductCode()).isEqualTo("P001");
        assertThat(inv.getAvailableQuantity()).isEqualTo(100);
        assertThat(inv.getReservedItems()).isEqualTo(5);
        assertThat(inv.getVersion()).isEqualTo((short) 1);

        Inventory parameterized = new Inventory(20L, "P002", null, null);
        assertThat(parameterized.getId()).isEqualTo(20L);
        assertThat(parameterized.getProductCode()).isEqualTo("P002");
        assertThat(parameterized.getAvailableQuantity()).isEqualTo(0);
        assertThat(parameterized.getReservedItems()).isEqualTo(0);
    }
}
