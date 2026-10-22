/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serial;
import java.io.Serializable;
import org.springframework.modulith.events.Externalized;

@Externalized("inventory.exchange::inventory.processed")
public record InventoryProcessedEvent(@JsonValue InventoryReservationResult result)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @JsonCreator
    public InventoryProcessedEvent(InventoryReservationResult result) {
        this.result = result;
    }
}
