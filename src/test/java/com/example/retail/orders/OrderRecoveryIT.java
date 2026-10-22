/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.retail.catalog.CatalogFacade;
import com.example.retail.catalog.ContainersConfig;
import com.example.retail.inventory.InventoryFacade;
import com.example.retail.orders.internal.application.OrderRequestFingerprinter;
import com.example.retail.orders.internal.application.OrdersFacadeImpl;
import com.example.retail.orders.internal.domain.*;
import com.example.retail.orders.internal.persistence.*;
import com.example.retail.payments.PaymentsFacade;
import com.example.retail.shared.inventory.*;
import com.example.retail.shared.orders.*;
import com.example.retail.shared.payments.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {"spring.liquibase.contexts=sample-data", "orders.recovery.delay-ms=3600000"})
@Import(ContainersConfig.class)
class OrderRecoveryIT {
    @Autowired OrdersFacade facade;
    @Autowired JdbcClient jdbc;
    @MockitoSpyBean OrderRepository orders;
    @Autowired OrderIdempotencyRepository claims;
    @Autowired CatalogFacade catalog;
    @Autowired ApplicationEventPublisher events;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean InventoryFacade inventory;
    @MockitoSpyBean PaymentsFacade payments;

    private OrderRequest request(long customer, int quantity) {
        return new OrderRequest(
                customer,
                List.of(new OrderItemRequest("P005", quantity, BigDecimal.ONE)),
                new Address("Street", null, "City", "State", "12345", "US"));
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private OrderIdempotency claim(long customer, String key) {
        return claims.findByCustomerIdAndIdempotencyKey(customer, key).orElseThrow();
    }

    private OrdersFacadeImpl freshWorker() {
        return new OrdersFacadeImpl(
                orders,
                claims,
                catalog,
                inventory,
                payments,
                new OrderRequestFingerprinter(),
                events,
                mapper,
                manager);
    }

    @Test
    void precommitIsVisibleAndCapturedPaymentRecoversFromPersistedState() {
        var balance = payments.findCustomerById(101L).orElseThrow().amountAvailable();
        int stock = inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity();
        String key = key();
        doAnswer(
                        invocation -> {
                            InventoryReservationRequest reservation = invocation.getArgument(0);
                            var independent = new TransactionTemplate(manager);
                            independent.setPropagationBehavior(
                                    TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                            independent.execute(
                                    tx -> {
                                        assertThat(
                                                        orders.findOrderById(reservation.orderId())
                                                                .orElseThrow()
                                                                .getStatus())
                                                .isEqualTo(OrderStatus.NEW);
                                        assertThat(claim(101L, key).getOrderId())
                                                .isEqualTo(reservation.orderId());
                                        return null;
                                    });
                            return invocation.callRealMethod();
                        })
                .when(inventory)
                .reserve(any());
        doThrow(new IllegalStateException("process interrupted after capture"))
                .doCallRealMethod()
                .when(inventory)
                .consume(anyString());
        assertThatThrownBy(() -> facade.createOrder(request(101L, 1), key))
                .hasMessageContaining("interrupted");
        OrderIdempotency pending = claim(101L, key);
        assertThat(pending.getStatus()).isEqualTo(OrderIdempotency.STATUS_PROCESSING);
        assertThat(orders.findOrderById(pending.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.NEW);
        assertThat(payments.getPaymentStatus(pending.getOperationKey()).orElseThrow().status())
                .isEqualTo(PaymentStatus.CONFIRMED);
        reset(inventory);
        // A new worker has no in-memory workflow state, as after process restart.
        freshWorker().recoverPendingOrders();
        freshWorker().recoverPendingOrders();
        var replay = facade.createOrder(request(101L, 1), key);
        assertThat(replay.orderId()).isEqualTo(pending.getOrderId());
        assertThat(replay.status()).isEqualTo("CONFIRMED");
        assertThat(replay.totalPrice()).isEqualByComparingTo("249.99");
        assertThat(payments.findCustomerById(101L).orElseThrow().amountAvailable())
                .isEqualByComparingTo(balance.subtract(new BigDecimal("249.99")));
        assertThat(inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity())
                .isEqualTo(stock - 1);
    }

    @Test
    void paymentExceptionPersistsFailedAndCompensationSurvivesRetry() {
        int stock = inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity();
        String key = key();
        doThrow(new IllegalStateException("payment unavailable")).when(payments).reserve(any());
        doThrow(new IllegalStateException("compensation unavailable"))
                .doCallRealMethod()
                .when(inventory)
                .cancelReservation(anyString());
        assertThatThrownBy(() -> facade.createOrder(request(101L, 1), key))
                .hasMessage("payment unavailable");
        var pending = claim(101L, key);
        assertThat(orders.findOrderById(pending.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.FAILED);
        assertThat(pending.getStatus()).isEqualTo(OrderIdempotency.STATUS_COMPENSATING);
        freshWorker().recoverPendingOrders();
        freshWorker().recoverPendingOrders();
        assertThat(claim(101L, key).getStatus()).isEqualTo(OrderIdempotency.STATUS_FAILED);
        assertThat(inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity())
                .isEqualTo(stock);
    }

    @Test
    void cancellationRefundFailureRemainsPendingAndRestocksOnce() {
        var balance = payments.findCustomerById(101L).orElseThrow().amountAvailable();
        int stock = inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity();
        String key = key();
        var order = facade.createOrder(request(101L, 1), key);
        doThrow(new IllegalStateException("refund unavailable"))
                .doCallRealMethod()
                .when(payments)
                .refund(any());
        assertThatThrownBy(() -> facade.cancelOrder(order.orderId()))
                .hasMessage("refund unavailable");
        assertThat(orders.findOrderById(order.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(claim(101L, key).getStatus()).isEqualTo(OrderIdempotency.STATUS_CANCELLING);
        freshWorker().recoverPendingOrders();
        facade.cancelOrder(order.orderId());
        assertThat(orders.findOrderById(order.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ROLLBACK);
        assertThat(payments.findCustomerById(101L).orElseThrow().amountAvailable())
                .isEqualByComparingTo(balance);
        assertThat(inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity())
                .isEqualTo(stock);
    }

    @Test
    void cancellationOfNewOrderReleasesReservations() {
        var balance = payments.findCustomerById(101L).orElseThrow().amountAvailable();
        int stock = inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity();
        String key = key();
        doThrow(new IllegalStateException("interrupted before capture"))
                .when(payments)
                .confirm(anyString());
        assertThatThrownBy(() -> facade.createOrder(request(101L, 1), key))
                .hasMessageContaining("interrupted");
        var pending = claim(101L, key);
        facade.cancelOrder(pending.getOrderId());
        facade.cancelOrder(pending.getOrderId());
        assertThat(orders.findOrderById(pending.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ROLLBACK);
        assertThat(payments.findCustomerById(101L).orElseThrow().amountAvailable())
                .isEqualByComparingTo(balance);
        assertThat(inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity())
                .isEqualTo(stock);
        assertThat(inventory.getReservationStatus(pending.getOperationKey()).orElseThrow().status())
                .isEqualTo(InventoryReservationStatus.RELEASED);
    }

    @Test
    void batchAndCrossCustomerKeysAreIsolated() {
        var results = facade.saveBatchOrders(List.of(request(102L, 20), request(101L, 1)));
        assertThat(results)
                .extracting(OrderResponse::status)
                .containsExactly("SERVICE_UNAVAILABLE", "CONFIRMED");
        assertThat(orders.findOrderById(results.get(1).orderId())).isPresent();
        String sharedKey = key();
        var first = facade.createOrder(request(101L, 1), sharedKey);
        var second = facade.createOrder(request(103L, 1), sharedKey);
        assertThat(first.orderId()).isNotEqualTo(second.orderId());
        assertThat(claim(101L, sharedKey).getOperationKey())
                .isNotEqualTo(claim(103L, sharedKey).getOperationKey());
    }

    @Test
    void similaritySearchAcceptsNullFiltersAndExecutesCountQuery() {
        var result =
                orders.searchOrdersBySimilarity(
                        "WEB",
                        0.1,
                        null,
                        null,
                        org.springframework.data.domain.PageRequest.of(0, 1));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isGreaterThan(1);
        assertThat(facade.searchOrders("WEB", "keyword", null, "CONFIRMED", null, 0, 10))
                .isNotNull();
    }

    @Test
    void consumedInventoryAndCapturedPaymentSurviveOrderCommitFailureWithoutDuplication() {
        var balance = payments.findCustomerById(101L).orElseThrow().amountAvailable();
        int stock = inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity();
        String key = key();
        doThrow(new IllegalStateException("order write failed"))
                .when(orders)
                .save(argThat(order -> order.getStatus() == OrderStatus.CONFIRMED));
        assertThatThrownBy(() -> facade.createOrder(request(101L, 1), key))
                .hasMessage("order write failed");
        reset(orders);
        var pending = claim(101L, key);
        assertThat(inventory.getReservationStatus(pending.getOperationKey()).orElseThrow().status())
                .isEqualTo(InventoryReservationStatus.CONSUMED);
        assertThat(orders.findOrderById(pending.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.NEW);
        freshWorker().recoverPendingOrders();
        assertThat(facade.createOrder(request(101L, 1), key).status()).isEqualTo("CONFIRMED");
        assertThat(payments.findCustomerById(101L).orElseThrow().amountAvailable())
                .isEqualByComparingTo(balance.subtract(new BigDecimal("249.99")));
        assertThat(inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity())
                .isEqualTo(stock - 1);
    }

    private void changeClaim(Long id, Consumer<OrderIdempotency> mutation) {
        var independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        independent.executeWithoutResult(
                tx -> {
                    jdbc.sql("SET LOCAL lock_timeout = '1s'").update();
                    OrderIdempotency claim = claims.findLockedById(id).orElseThrow();
                    mutation.accept(claim);
                    claims.save(claim);
                });
    }

    @Test
    void businessStepDoesNotHoldClaimLockAndActiveLeaseRejectsCompetingWorkers() {
        String key = key();
        doAnswer(
                        invocation -> {
                            InventoryReservationRequest reservation = invocation.getArgument(0);
                            OrderIdempotency current = claim(101L, key);
                            changeClaim(
                                    current.getId(),
                                    locked -> {
                                        assertThat(locked.getClaimOwner()).isNotBlank();
                                        assertThat(locked.getLeaseExpiresAt())
                                                .isAfter(LocalDateTime.now());
                                    });
                            assertThatThrownBy(
                                            () -> freshWorker().cancelOrder(reservation.orderId()))
                                    .isInstanceOf(IdempotencyConflictException.class);
                            freshWorker().recoverPendingOrders();
                            return invocation.callRealMethod();
                        })
                .when(inventory)
                .reserve(any());

        var result = facade.createOrder(request(101L, 1), key);

        verify(inventory, times(1)).reserve(argThat(r -> r.orderId().equals(result.orderId())));
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(claim(101L, key).getClaimOwner()).isNull();
        assertThat(claim(101L, key).getLeaseExpiresAt()).isNull();
    }

    @Test
    void expiredWorkerCannotOverwriteCancellationByNewOwner() {
        String key = key();
        var balance = payments.findCustomerById(101L).orElseThrow().amountAvailable();
        int stock = inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity();
        AtomicBoolean consumed = new AtomicBoolean();
        doAnswer(
                        invocation -> {
                            Object result = invocation.callRealMethod();
                            consumed.set(true);
                            return result;
                        })
                .when(inventory)
                .consume(anyString());
        OrderRepository workerOrders =
                mock(OrderRepository.class, org.mockito.AdditionalAnswers.delegatesTo(orders));
        doAnswer(
                        invocation -> {
                            var result = orders.findOrderById(invocation.getArgument(0));
                            // Take over after the consume transaction commits, before finalization
                            // starts.
                            if (consumed.compareAndSet(true, false)) {
                                var current = claim(101L, key);
                                changeClaim(
                                        current.getId(),
                                        c ->
                                                c.setLeaseExpiresAt(
                                                        LocalDateTime.now().minusMinutes(1)));
                                freshWorker().cancelOrder(current.getOrderId());
                            }
                            return result;
                        })
                .when(workerOrders)
                .findOrderById(anyLong());
        var staleWorker =
                new OrdersFacadeImpl(
                        workerOrders,
                        claims,
                        catalog,
                        inventory,
                        payments,
                        new OrderRequestFingerprinter(),
                        events,
                        mapper,
                        manager);

        assertThatThrownBy(() -> staleWorker.createOrder(request(101L, 1), key))
                .isInstanceOf(IdempotencyConflictException.class);

        var current = claim(101L, key);
        assertThat(current.getStatus()).isEqualTo(OrderIdempotency.STATUS_COMPLETED);
        assertThat(orders.findOrderById(current.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ROLLBACK);
        assertThat(facade.createOrder(request(101L, 1), key).status()).isEqualTo("ROLLBACK");
        assertThat(payments.findCustomerById(101L).orElseThrow().amountAvailable())
                .isEqualByComparingTo(balance);
        assertThat(inventory.findInventoryByProductCode("P005").orElseThrow().availableQuantity())
                .isEqualTo(stock);
    }

    @Test
    void replacedOwnerCannotCompleteOrReleaseAnotherWorkersProcessingClaim() {
        String key = key();
        doAnswer(
                        invocation -> {
                            Object result = invocation.callRealMethod();
                            var current = claim(101L, key);
                            changeClaim(current.getId(), c -> c.setClaimOwner("replacement-owner"));
                            return result;
                        })
                .doCallRealMethod()
                .when(inventory)
                .consume(anyString());

        assertThatThrownBy(() -> facade.createOrder(request(101L, 1), key))
                .isInstanceOf(IdempotencyConflictException.class);

        var current = claim(101L, key);
        assertThat(current.getStatus()).isEqualTo(OrderIdempotency.STATUS_PROCESSING);
        assertThat(current.getClaimOwner()).isEqualTo("replacement-owner");
        assertThat(orders.findOrderById(current.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.NEW);
        changeClaim(current.getId(), c -> c.setLeaseExpiresAt(LocalDateTime.now().minusMinutes(1)));
        freshWorker().recoverPendingOrders();
        assertThat(claim(101L, key).getStatus()).isEqualTo(OrderIdempotency.STATUS_COMPLETED);
        assertThat(claim(101L, key).getClaimOwner()).isNull();
        assertThat(orders.findOrderById(current.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }
}
