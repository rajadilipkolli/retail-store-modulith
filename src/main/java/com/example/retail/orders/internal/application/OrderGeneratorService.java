/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.application;

import com.example.retail.orders.OrdersFacade;
import com.example.retail.orders.internal.domain.OrderGeneration;
import com.example.retail.orders.internal.persistence.OrderGenerationRepository;
import com.example.retail.shared.orders.Address;
import com.example.retail.shared.orders.OrderItemRequest;
import com.example.retail.shared.orders.OrderRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OrderGeneratorService.class);

    public static final int MAX_GENERATION_BATCH_SIZE = 10_000;
    private static final int NUM_ORDERS = 10;
    private static final int BATCH_SIZE = 10;

    private static final List<Long> CUSTOMER_IDS = List.of(101L, 102L, 103L, 104L, 105L);
    private static final BigDecimal GENERATED_ORDER_PRICE = BigDecimal.valueOf(10.00);

    private final OrdersFacade ordersFacade;
    private final OrderGenerationRepository generationRepository;

    public OrderGeneratorService(
            OrdersFacade ordersFacade, OrderGenerationRepository generationRepository) {
        this.ordersFacade = ordersFacade;
        this.generationRepository = generationRepository;
    }

    public void generateOrders(String idempotencyKey, Integer batchSize) {
        validateBatchSize(batchSize);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }

        int resolvedBatchSize = batchSize != null ? batchSize : NUM_ORDERS;
        int totalBatches = (resolvedBatchSize + BATCH_SIZE - 1) / BATCH_SIZE;

        boolean created =
                generationRepository.createInitial(idempotencyKey, resolvedBatchSize, totalBatches);
        int startBatchIndex = 0;

        if (!created) {
            Optional<OrderGeneration> existingOpt =
                    generationRepository.findByIdempotencyKey(idempotencyKey);
            if (existingOpt.isEmpty()) {
                return;
            }
            OrderGeneration existing = existingOpt.get();
            if (OrderGeneration.STATUS_COMPLETED.equals(existing.status())) {
                log.info("Order generation for key '{}' is already COMPLETED", idempotencyKey);
                return;
            }
            if (OrderGeneration.STATUS_IN_PROGRESS.equals(existing.status())) {
                log.warn("Order generation for key '{}' is already IN_PROGRESS", idempotencyKey);
                return;
            }
            if (OrderGeneration.STATUS_FAILED.equals(existing.status())) {
                log.info(
                        "Resuming failed order generation for key '{}' from batch {} of {}",
                        idempotencyKey,
                        existing.completedBatches(),
                        existing.totalBatches());
                startBatchIndex = existing.completedBatches();
                resolvedBatchSize = existing.batchSize();
                totalBatches = existing.totalBatches();
                generationRepository.markResumed(idempotencyKey);
            }
        }

        try {
            for (int batchIndex = startBatchIndex; batchIndex < totalBatches; batchIndex++) {
                int fromIndex = batchIndex * BATCH_SIZE;
                int toIndex = Math.min(fromIndex + BATCH_SIZE, resolvedBatchSize);

                List<OrderRequest> batchRequests = new ArrayList<>(toIndex - fromIndex);
                for (int i = fromIndex; i < toIndex; i++) {
                    long customerId = CUSTOMER_IDS.get(i % CUSTOMER_IDS.size());
                    batchRequests.add(
                            new OrderRequest(
                                    customerId,
                                    generateOrderItems(),
                                    addressForCustomer(customerId)));
                }

                ordersFacade.saveBatchOrders(batchRequests);
                generationRepository.updateProgress(idempotencyKey, batchIndex + 1);
            }
            generationRepository.markCompleted(idempotencyKey, totalBatches);
        } catch (RuntimeException ex) {
            log.error("Order generation failed for key '{}'", idempotencyKey, ex);
            generationRepository.markFailed(idempotencyKey, ex.getMessage());
            throw ex;
        }
    }

    private static void validateBatchSize(Integer batchSize) {
        if (batchSize != null && (batchSize < 1 || batchSize > MAX_GENERATION_BATCH_SIZE)) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and " + MAX_GENERATION_BATCH_SIZE);
        }
    }

    private List<OrderItemRequest> generateOrderItems() {
        return List.of(new OrderItemRequest("P001", 1, GENERATED_ORDER_PRICE));
    }

    private Address addressForCustomer(long customerId) {
        return switch ((int) customerId) {
            case 101 -> new Address("123 Main St", "Apt 4B", "New York", "NY", "10001", "USA");
            case 102 -> new Address("456 Oak Ave", null, "Los Angeles", "CA", "90210", "USA");
            case 103 -> new Address("789 Pine Rd", null, "Chicago", "IL", "60601", "USA");
            case 104 -> new Address("123 Raja St", null, "Raja City", "RA", "12345", "USA");
            default -> new Address("123 Retail St", null, "Retail City", "RE", "12345", "USA");
        };
    }
}
