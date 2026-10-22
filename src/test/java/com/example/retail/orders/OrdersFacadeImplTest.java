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
import com.example.retail.inventory.InventoryFacade;
import com.example.retail.orders.internal.application.OrderRequestFingerprinter;
import com.example.retail.orders.internal.application.OrdersFacadeImpl;
import com.example.retail.orders.internal.domain.*;
import com.example.retail.orders.internal.persistence.*;
import com.example.retail.payments.PaymentsFacade;
import com.example.retail.shared.catalog.ProductDto;
import com.example.retail.shared.inventory.*;
import com.example.retail.shared.orders.*;
import com.example.retail.shared.payments.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

class OrdersFacadeImplTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderIdempotencyRepository claims = mock(OrderIdempotencyRepository.class);
    private final CatalogFacade catalog = mock(CatalogFacade.class);
    private final InventoryFacade inventory = mock(InventoryFacade.class);
    private final PaymentsFacade payments = mock(PaymentsFacade.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Map<Long, Order> orderState = new HashMap<>();
    private final Map<Long, OrderIdempotency> claimState = new HashMap<>();
    private OrdersFacadeImpl facade;

    @BeforeEach
    void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        facade =
                new OrdersFacadeImpl(
                        orders,
                        claims,
                        catalog,
                        inventory,
                        payments,
                        new OrderRequestFingerprinter(),
                        events,
                        JsonMapper.builder().findAndAddModules().build(),
                        manager);
        when(orders.save(any()))
                .thenAnswer(
                        inv -> {
                            Order order = inv.getArgument(0);
                            if (order.getId() == null) order.setId((long) orderState.size() + 1);
                            orderState.put(order.getId(), order);
                            return order;
                        });
        when(orders.findOrderById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(orderState.get(inv.getArgument(0))));
        when(claims.saveAndFlush(any()))
                .thenAnswer(
                        inv -> {
                            OrderIdempotency claim = inv.getArgument(0);
                            claim.setId(claim.getOrderId());
                            claimState.put(claim.getId(), claim);
                            return claim;
                        });
        when(claims.findLockedById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(claimState.get(inv.getArgument(0))));
        when(claims.findByOrderId(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(claimState.get(inv.getArgument(0))));
        when(claims.findByCustomerIdAndIdempotencyKey(anyLong(), anyString()))
                .thenAnswer(
                        inv ->
                                claimState.values().stream()
                                        .filter(
                                                c ->
                                                        c.getCustomerId().equals(inv.getArgument(0))
                                                                && c.getIdempotencyKey()
                                                                        .equals(inv.getArgument(1)))
                                        .findFirst());
        when(claims.findByStatusIn(anyList()))
                .thenAnswer(
                        inv ->
                                claimState.values().stream()
                                        .filter(
                                                c ->
                                                        ((List<?>) inv.getArgument(0))
                                                                .contains(c.getStatus()))
                                        .toList());
        when(payments.findCustomerById(101L)).thenReturn(Optional.of(mock(CustomerResponse.class)));
        when(catalog.findProductByCode("P001"))
                .thenReturn(
                        Optional.of(
                                new ProductDto(
                                        1L, "P001", "Product", "", "", new BigDecimal("25.00"))));
        when(inventory.reserve(any()))
                .thenAnswer(
                        inv -> {
                            InventoryReservationRequest request = inv.getArgument(0);
                            return new InventoryReservationResult(
                                    request.idempotencyKey(),
                                    request.orderId(),
                                    InventoryReservationStatus.RESERVED,
                                    "ok",
                                    request.items());
                        });
        when(payments.reserve(any())).thenReturn(payment(PaymentStatus.RESERVED));
    }

    private OrderRequest request() {
        return new OrderRequest(
                101L,
                List.of(new OrderItemRequest("P001", 2, BigDecimal.ONE)),
                new Address("Street", null, "City", "State", "12345", "US"));
    }

    private PaymentResult payment(PaymentStatus status) {
        return new PaymentResult(
                "order-1", 101L, 1L, new BigDecimal("50.00"), "USD", status, "result");
    }

    private void reservedStock() {
        when(inventory.getReservationStatus("order-1"))
                .thenReturn(
                        Optional.of(
                                new InventoryReservationResult(
                                        "order-1",
                                        1L,
                                        InventoryReservationStatus.RESERVED,
                                        "ok",
                                        List.of(new ReservationItem("P001", 2)))));
    }

    @Test
    void authoritativePriceAndPostCommitEvents() {
        var result = facade.createOrder(request(), "key");
        assertThat(result.totalPrice()).isEqualByComparingTo("50.00");
        assertThat(result.items().getFirst().productPrice()).isEqualByComparingTo("25.00");
        verify(payments).reserve(argThat(r -> r.amount().compareTo(new BigDecimal("50.00")) == 0));
        verify(events).publishEvent(any(OrderConfirmedEvent.class));
        verify(events).publishEvent(any(OrderDto.class));
    }

    @Test
    void missingCatalogProductHasNoSideEffects() {
        when(catalog.findProductByCode("P001")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.createOrder(request(), "key"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(inventory);
        verify(payments, never()).reserve(any());
        assertThat(orderState).isEmpty();
    }

    @Test
    void inventoryFailureIsRecorded() {
        doThrow(new IllegalArgumentException("stock failure")).when(inventory).reserve(any());
        assertThatThrownBy(() -> facade.createOrder(request(), "key")).hasMessage("stock failure");
        assertThat(orderState.get(1L).getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(claimState.get(1L).getStatus()).isEqualTo(OrderIdempotency.STATUS_FAILED);
        verify(payments, never()).reserve(any());
    }

    @Test
    void paymentFailureCompensatesInventory() {
        reservedStock();
        when(payments.reserve(any())).thenReturn(payment(PaymentStatus.FAILED));
        assertThatThrownBy(() -> facade.createOrder(request(), "key"))
                .hasMessageContaining("Payment reservation failed");
        verify(inventory).cancelReservation("order-1");
        assertThat(orderState.get(1L).getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void compensationFailureRemainsRetryable() {
        reservedStock();
        when(payments.reserve(any())).thenThrow(new IllegalStateException("payment down"));
        when(inventory.cancelReservation("order-1"))
                .thenThrow(new IllegalStateException("stock down"));
        assertThatThrownBy(() -> facade.createOrder(request(), "key")).hasMessage("payment down");
        assertThat(claimState.get(1L).getStatus()).isEqualTo(OrderIdempotency.STATUS_COMPENSATING);
    }

    @Test
    void identicalReplayReturnsOriginalPriceSnapshot() {
        var first = facade.createOrder(request(), "key");
        when(catalog.findProductByCode("P001")).thenReturn(Optional.empty());
        assertThat(facade.createOrder(request(), "key")).isEqualTo(first);
        verify(inventory, times(1)).reserve(any());
        verify(payments, times(1)).reserve(any());
    }

    @Test
    void mismatchedFailedRequestStillConflicts() {
        doThrow(new IllegalArgumentException("failed")).when(inventory).reserve(any());
        assertThatThrownBy(() -> facade.createOrder(request(), "key"))
                .isInstanceOf(IllegalArgumentException.class);
        var altered =
                new OrderRequest(
                        101L,
                        List.of(new OrderItemRequest("P001", 3, BigDecimal.ONE)),
                        request().deliveryAddress());
        assertThatThrownBy(() -> facade.createOrder(altered, "key"))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(orderState).hasSize(1);
    }

    @Test
    void confirmationFailureCanResume() {
        when(inventory.consume("order-1")).thenThrow(new IllegalStateException("interrupted"));
        assertThatThrownBy(() -> facade.createOrder(request(), "key")).hasMessage("interrupted");
        assertThat(claimState.get(1L).getStatus()).isEqualTo(OrderIdempotency.STATUS_PROCESSING);
        assertThatThrownBy(() -> facade.createOrder(request(), "key"))
                .isInstanceOf(IdempotencyConflictException.class);
        doReturn(null).when(inventory).consume("order-1");
        facade.recoverPendingOrders();
        assertThat(orderState.get(1L).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void refundFailureDoesNotCompleteCancellation() {
        facade.createOrder(request(), "key");
        reservedStock();
        when(payments.getPaymentStatus("order-1"))
                .thenReturn(Optional.of(payment(PaymentStatus.CONFIRMED)));
        when(payments.refund(any())).thenThrow(new IllegalStateException("refund down"));
        assertThatThrownBy(() -> facade.cancelOrder(1L)).hasMessage("refund down");
        assertThat(orderState.get(1L).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(claimState.get(1L).getStatus()).isEqualTo(OrderIdempotency.STATUS_CANCELLING);
        doReturn(payment(PaymentStatus.REFUNDED)).when(payments).refund(any());
        facade.cancelOrder(1L);
        assertThat(orderState.get(1L).getStatus()).isEqualTo(OrderStatus.ROLLBACK);
        verify(inventory).cancelReservation("order-1");
        assertThat(facade.createOrder(request(), "key").status()).isEqualTo("CONFIRMED");
    }

    @Test
    void batchReportsEachFailureAndContinues() {
        var invalid = new OrderRequest(101L, List.of(), request().deliveryAddress());
        var results = facade.saveBatchOrders(List.of(invalid, request()));
        assertThat(results)
                .extracting(OrderResponse::status)
                .containsExactly("SERVICE_UNAVAILABLE", "CONFIRMED");
    }

    @Test
    void concurrentClaimCollisionIsConflict() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(claims).saveAndFlush(any());
        assertThatThrownBy(() -> facade.createOrder(request(), "key"))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(inventory, never()).reserve(any());
    }

    @Test
    void legacyClaimsRetainOriginalReservationKey() {
        facade.createOrder(request(), "legacy");
        claimState.get(1L).setOperationKey(null);
        when(payments.getPaymentStatus("legacy"))
                .thenReturn(Optional.of(payment(PaymentStatus.CONFIRMED)));
        when(inventory.getReservationStatus("legacy"))
                .thenReturn(
                        Optional.of(
                                new InventoryReservationResult(
                                        "legacy",
                                        1L,
                                        InventoryReservationStatus.CONSUMED,
                                        "ok",
                                        List.of(new ReservationItem("P001", 2)))));
        facade.cancelOrder(1L);
        verify(inventory).cancelReservation("legacy");
        verify(payments)
                .refund(argThat(r -> r.orderId().equals(1L) && r.customerId().equals(101L)));
        assertThat(orderState.get(1L).getStatus()).isEqualTo(OrderStatus.ROLLBACK);
    }
}
