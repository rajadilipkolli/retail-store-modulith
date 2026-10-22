/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.inventory.internal.domain.InventoryReservation;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryReservationTest {

    @Test
    @DisplayName("InventoryReservation entity accessors and constructors operate correctly")
    void inventoryReservationEntityAccessors() {
        Instant now = Instant.now();
        InventoryReservation res =
                new InventoryReservation(
                        100L,
                        "key-1",
                        50L,
                        InventoryReservationStatus.RESERVED,
                        "[{\"productCode\":\"P001\",\"quantity\":2}]",
                        now,
                        now);

        assertThat(res.getId()).isEqualTo(100L);
        assertThat(res.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(res.getOrderId()).isEqualTo(50L);
        assertThat(res.getStatus()).isEqualTo(InventoryReservationStatus.RESERVED);
        assertThat(res.getItemsJson()).contains("P001");
        assertThat(res.getCreatedAt()).isEqualTo(now);
        assertThat(res.getUpdatedAt()).isEqualTo(now);

        InventoryReservation defaultObj = new InventoryReservation();
        defaultObj.setId(200L);
        defaultObj.setIdempotencyKey("key-2");
        defaultObj.setOrderId(60L);
        defaultObj.setStatus(InventoryReservationStatus.RELEASED);
        defaultObj.setItemsJson("[]");
        Instant t1 = Instant.now().minusSeconds(10);
        Instant t2 = Instant.now();
        defaultObj.setCreatedAt(t1);
        defaultObj.setUpdatedAt(t2);
        defaultObj.setVersion((short) 2);

        assertThat(defaultObj.getId()).isEqualTo(200L);
        assertThat(defaultObj.getIdempotencyKey()).isEqualTo("key-2");
        assertThat(defaultObj.getOrderId()).isEqualTo(60L);
        assertThat(defaultObj.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(defaultObj.getItemsJson()).isEqualTo("[]");
        assertThat(defaultObj.getCreatedAt()).isEqualTo(t1);
        assertThat(defaultObj.getUpdatedAt()).isEqualTo(t2);
        assertThat(defaultObj.getVersion()).isEqualTo((short) 2);
    }
}
