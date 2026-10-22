/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.orders.internal.application.OrderGeneratorService;
import com.example.retail.orders.internal.domain.OrderGeneration;
import com.example.retail.orders.internal.persistence.OrderGenerationRepository;
import com.example.retail.orders.internal.persistence.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@Import(ContainersConfig.class)
class OrderGeneratorIT {

    @Autowired private OrderGeneratorService generatorService;
    @Autowired private OrderGenerationRepository generationRepository;
    @Autowired private OrderRepository orderRepository;

    @Test
    @DisplayName(
            "generateOrders end-to-end persists orders, records durable status, and is idempotent")
    void generateOrdersEndToEndAndIdempotent() {
        String key = "it-gen-key-e2e-" + System.nanoTime();
        long initialCount = orderRepository.count();

        // Generate 12 orders (batch size 10 means 2 batches: 10 + 2)
        generatorService.generateOrders(key, 12);

        long afterFirstCount = orderRepository.count();
        assertThat(afterFirstCount).isEqualTo(initialCount + 12);

        // Verify durable record in order_generation table
        Optional<OrderGeneration> recordOpt = generationRepository.findByIdempotencyKey(key);
        assertThat(recordOpt).isPresent();
        OrderGeneration record = recordOpt.get();
        assertThat(record.status()).isEqualTo(OrderGeneration.STATUS_COMPLETED);
        assertThat(record.batchSize()).isEqualTo(12);
        assertThat(record.totalBatches()).isEqualTo(2);
        assertThat(record.completedBatches()).isEqualTo(2);

        // Replay with identical key: should return immediately without creating duplicate orders
        generatorService.generateOrders(key, 12);

        long afterReplayCount = orderRepository.count();
        assertThat(afterReplayCount).isEqualTo(afterFirstCount);
    }

    @Test
    void onlyOneWorkerCanResumeFailedGeneration() throws Exception {
        String key = "resume-race-" + System.nanoTime();
        generationRepository.createInitial(key, 25, 3);
        generationRepository.updateProgress(key, 1);
        assertThat(generationRepository.markResumed(key)).isFalse();
        generationRepository.markFailed(key, "retry");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks =
                    java.util.stream.IntStream.range(0, 2)
                            .mapToObj(
                                    i ->
                                            executor.submit(
                                                    () -> {
                                                        assertThat(
                                                                        generationRepository
                                                                                .findByIdempotencyKey(
                                                                                        key)
                                                                                .orElseThrow()
                                                                                .status())
                                                                .isEqualTo(
                                                                        OrderGeneration
                                                                                .STATUS_FAILED);
                                                        ready.countDown();
                                                        assertThat(
                                                                        start.await(
                                                                                10,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                        return generationRepository.markResumed(
                                                                key);
                                                    }))
                            .toList();
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(
                            List.of(
                                    tasks.get(0).get(10, TimeUnit.SECONDS),
                                    tasks.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var resumed = generationRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(resumed.status()).isEqualTo(OrderGeneration.STATUS_IN_PROGRESS);
        assertThat(resumed.completedBatches()).isEqualTo(1);
        assertThat(resumed.errorMessage()).isNull();
        generationRepository.markCompleted(key, 3);
        assertThat(generationRepository.markResumed(key)).isFalse();
        assertThat(generationRepository.markResumed("missing-key")).isFalse();
    }
}
