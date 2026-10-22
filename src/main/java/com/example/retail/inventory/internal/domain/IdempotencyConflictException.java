/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.domain;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }

    public static IdempotencyConflictException forKey(String idempotencyKey) {
        return new IdempotencyConflictException(
                "Idempotency key '%s' was already used with a different request payload"
                        .formatted(idempotencyKey));
    }
}
