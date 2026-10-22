/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.application;

import com.example.retail.orders.OrdersFacade;
import com.example.retail.shared.orders.Address;
import com.example.retail.shared.orders.OrderItemRequest;
import com.example.retail.shared.orders.OrderRequest;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderGeneratorService {

    public static final int MAX_GENERATION_BATCH_SIZE = 10_000;
    private static final int NUM_ORDERS = 10;
    private static final int BATCH_SIZE = 10;
    private static final SecureRandom RAND = new SecureRandom();

    private final Set<String> processedIdempotencyKeys = ConcurrentHashMap.newKeySet();
    private final OrdersFacade ordersFacade;

    public OrderGeneratorService(OrdersFacade ordersFacade) {
        this.ordersFacade = ordersFacade;
    }

    @Async
    public void generateOrders(String idempotencyKey, Integer batchSize) {
        validateBatchSize(batchSize);
        if (!processedIdempotencyKeys.add(idempotencyKey)) {
            return;
        }

        int resolvedBatchSize = batchSize != null ? batchSize : NUM_ORDERS;

        IntStream.range(0, resolvedBatchSize)
                .boxed()
                .collect(Collectors.groupingBy(i -> i / BATCH_SIZE))
                .values()
                .forEach(
                        batch -> {
                            List<OrderRequest> orderRequests =
                                    batch.stream()
                                            .map(
                                                    value -> {
                                                        List<OrderItemRequest> orderItems =
                                                                generateOrderItems();
                                                        long customerId = 101L;
                                                        return new OrderRequest(
                                                                customerId,
                                                                orderItems,
                                                                new Address(
                                                                        "123 Main St",
                                                                        "Apt 4B",
                                                                        "New York",
                                                                        "NY",
                                                                        "10001",
                                                                        "USA"));
                                                    })
                                            .toList();
                            ordersFacade.saveBatchOrders(orderRequests);
                        });
    }

    private static void validateBatchSize(Integer batchSize) {
        if (batchSize != null && (batchSize < 1 || batchSize > MAX_GENERATION_BATCH_SIZE)) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and " + MAX_GENERATION_BATCH_SIZE);
        }
    }

    private List<OrderItemRequest> generateOrderItems() {
        return List.of(
                new OrderItemRequest("P001", 1, BigDecimal.valueOf(999.99)),
                new OrderItemRequest("P005", 2, BigDecimal.valueOf(249.99)));
    }
}
