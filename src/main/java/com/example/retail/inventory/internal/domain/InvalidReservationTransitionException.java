/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.domain;

import com.example.retail.shared.inventory.InventoryReservationStatus;

public class InvalidReservationTransitionException extends RuntimeException {

    public InvalidReservationTransitionException(String message) {
        super(message);
    }

    public static InvalidReservationTransitionException forTransition(
            InventoryReservationStatus current, String operation) {
        return new InvalidReservationTransitionException(
                "Cannot perform '%s' on reservation in status '%s'".formatted(operation, current));
    }
}
