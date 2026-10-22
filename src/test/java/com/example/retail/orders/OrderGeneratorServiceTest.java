/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.retail.orders.internal.application.OrderGeneratorService;
import com.example.retail.orders.internal.domain.OrderGeneration;
import com.example.retail.orders.internal.persistence.OrderGenerationRepository;
import com.example.retail.shared.orders.OrderRequest;
import com.example.retail.shared.orders.OrderResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderGeneratorServiceTest {

    @Mock private OrdersFacade ordersFacade;
    @Mock private OrderGenerationRepository generationRepository;

    @Captor private ArgumentCaptor<List<OrderRequest>> batchCaptor;

    private OrderGeneratorService generatorService;

    @BeforeEach
    void setUp() {
        generatorService = new OrderGeneratorService(ordersFacade, generationRepository);
    }

    @Test
    @DisplayName("generateOrders completes all batches, sizes orders modestly, and marks completed")
    void generateOrdersSuccess() {
        String key = "gen-key-success";
        given(generationRepository.createInitial(eq(key), eq(25), eq(3))).willReturn(true);

        generatorService.generateOrders(key, 25);

        // Total 25 orders with batch size 10 means 3 batches (10, 10, 5)
        verify(ordersFacade, times(3)).saveBatchOrders(batchCaptor.capture());

        List<List<OrderRequest>> capturedBatches = batchCaptor.getAllValues();
        assertThat(capturedBatches).hasSize(3);
        assertThat(capturedBatches.get(0)).hasSize(10);
        assertThat(capturedBatches.get(1)).hasSize(10);
        assertThat(capturedBatches.get(2)).hasSize(5);

        // Verify order sizing and customer distribution across 101-105
        OrderRequest firstOrder = capturedBatches.get(0).get(0);
        assertThat(firstOrder.customerId()).isEqualTo(101L);
        assertThat(firstOrder.items()).hasSize(1);
        assertThat(firstOrder.items().get(0).productCode()).isEqualTo("P001");
        assertThat(firstOrder.items().get(0).quantity()).isEqualTo(1);
        assertThat(firstOrder.items().get(0).productPrice())
                .isEqualByComparingTo(BigDecimal.valueOf(10.00));

        OrderRequest secondOrder = capturedBatches.get(0).get(1);
        assertThat(secondOrder.customerId()).isEqualTo(102L);

        // Progress updates
        verify(generationRepository).updateProgress(key, 1);
        verify(generationRepository).updateProgress(key, 2);
        verify(generationRepository).updateProgress(key, 3);
        verify(generationRepository).markCompleted(key, 3);
    }

    @Test
    @DisplayName("generateOrders returns immediately if key is already completed")
    void generateOrdersAlreadyCompleted() {
        String key = "gen-key-completed";
        given(generationRepository.createInitial(eq(key), eq(10), eq(1))).willReturn(false);

        OrderGeneration completed =
                new OrderGeneration(
                        key,
                        10,
                        1,
                        1,
                        OrderGeneration.STATUS_COMPLETED,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now());
        given(generationRepository.findByIdempotencyKey(key)).willReturn(Optional.of(completed));

        generatorService.generateOrders(key, 10);

        verify(ordersFacade, never()).saveBatchOrders(any());
        verify(generationRepository, never()).updateProgress(anyString(), anyInt());
    }

    @Test
    @DisplayName("generateOrders returns immediately if key is currently in progress")
    void generateOrdersAlreadyInProgress() {
        String key = "gen-key-in-progress";
        given(generationRepository.createInitial(eq(key), eq(10), eq(1))).willReturn(false);

        OrderGeneration inProgress =
                new OrderGeneration(
                        key,
                        10,
                        1,
                        0,
                        OrderGeneration.STATUS_IN_PROGRESS,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now());
        given(generationRepository.findByIdempotencyKey(key)).willReturn(Optional.of(inProgress));

        generatorService.generateOrders(key, 10);

        verify(ordersFacade, never()).saveBatchOrders(any());
    }

    @Test
    @DisplayName("generateOrders resumes failed generation skipping already committed batches")
    void generateOrdersResumeFailed() {
        String key = "gen-key-resume";
        given(generationRepository.createInitial(eq(key), eq(25), eq(3))).willReturn(false);

        // Previous attempt finished batch 1 of 3 (batches 0 committed, failed on 1)
        OrderGeneration failed =
                new OrderGeneration(
                        key,
                        25,
                        3,
                        1,
                        OrderGeneration.STATUS_FAILED,
                        "Temporary failure",
                        LocalDateTime.now().minusMinutes(5),
                        LocalDateTime.now().minusMinutes(4));
        given(generationRepository.findByIdempotencyKey(key)).willReturn(Optional.of(failed));
        given(generationRepository.markResumed(key)).willReturn(true);

        generatorService.generateOrders(key, 25);

        verify(generationRepository).markResumed(key);

        // Should only execute batch 1 and batch 2 (2 calls to saveBatchOrders, skipping batch 0)
        verify(ordersFacade, times(2)).saveBatchOrders(batchCaptor.capture());

        List<List<OrderRequest>> capturedBatches = batchCaptor.getAllValues();
        assertThat(capturedBatches).hasSize(2);
        assertThat(capturedBatches.get(0)).hasSize(10); // Batch index 1 (items 10-19)
        assertThat(capturedBatches.get(1)).hasSize(5); // Batch index 2 (items 20-24)

        // Progress updates start from batch index 1 + 1 = 2
        verify(generationRepository, never()).updateProgress(key, 1);
        verify(generationRepository).updateProgress(key, 2);
        verify(generationRepository).updateProgress(key, 3);
        verify(generationRepository).markCompleted(key, 3);
    }

    @Test
    @DisplayName("generateOrders marks status FAILED when batch execution throws exception")
    void generateOrdersFailureMarksStatusFailed() {
        String key = "gen-key-fail";
        given(generationRepository.createInitial(eq(key), eq(10), eq(1))).willReturn(true);
        given(ordersFacade.saveBatchOrders(any()))
                .willThrow(new IllegalStateException("Simulated batch failure"));

        assertThatThrownBy(() -> generatorService.generateOrders(key, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated batch failure");

        verify(generationRepository).markFailed(key, "Simulated batch failure");
        verify(generationRepository, never()).markCompleted(anyString(), anyInt());
    }

    @Test
    void lostResumeClaimDoesNotProcessOrChangeProgress() {
        String key = "gen-lost-resume";
        given(generationRepository.findByIdempotencyKey(key))
                .willReturn(
                        Optional.of(
                                new OrderGeneration(
                                        key,
                                        25,
                                        3,
                                        1,
                                        OrderGeneration.STATUS_FAILED,
                                        "failure",
                                        LocalDateTime.now(),
                                        LocalDateTime.now())));
        given(generationRepository.markResumed(key)).willReturn(false);

        generatorService.generateOrders(key, 25);

        verify(ordersFacade, never()).saveBatchOrders(any());
        verify(generationRepository, never()).updateProgress(anyString(), anyInt());
        verify(generationRepository, never()).markCompleted(anyString(), anyInt());
        verify(generationRepository, never()).markFailed(anyString(), anyString());
    }

    @Test
    void failedOrderResponseDoesNotAdvanceBatchProgress() {
        String key = "gen-partial-failure";
        given(generationRepository.createInitial(key, 20, 2)).willReturn(true);
        OrderResponse success = org.mockito.Mockito.mock(OrderResponse.class);
        given(success.orderId()).willReturn(1L);
        given(ordersFacade.saveBatchOrders(any()))
                .willReturn(List.of(success), List.of(success, OrderResponse.emptyResponse(null)));

        assertThatThrownBy(() -> generatorService.generateOrders(key, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Batch contains failed orders");

        verify(generationRepository).updateProgress(key, 1);
        verify(generationRepository, never()).updateProgress(key, 2);
        verify(generationRepository).markFailed(key, "Batch contains failed orders");
        verify(generationRepository, never()).markCompleted(anyString(), anyInt());
    }

    @Test
    @DisplayName("generateOrders validates batch size boundaries and blank key")
    void generateOrdersValidations() {
        assertThatThrownBy(() -> generatorService.generateOrders("key", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize must be between 1 and 10000");

        assertThatThrownBy(() -> generatorService.generateOrders("key", 10_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize must be between 1 and 10000");

        assertThatThrownBy(() -> generatorService.generateOrders("   ", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency key must not be blank");
    }
}
