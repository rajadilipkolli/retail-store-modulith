/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.domain;

import java.time.LocalDateTime;

public record OrderGeneration(
        String idempotencyKey,
        int batchSize,
        int totalBatches,
        int completedBatches,
        String status,
        String errorMessage,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate) {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
