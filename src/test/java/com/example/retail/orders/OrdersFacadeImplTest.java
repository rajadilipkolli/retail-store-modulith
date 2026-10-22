/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.retail.catalog.CatalogFacade;
import com.example.retail.inventory.InventoryFacade;
import com.example.retail.orders.internal.application.OrderRequestFingerprinter;
import com.example.retail.orders.internal.application.OrdersFacadeImpl;
import com.example.retail.orders.internal.domain.IdempotencyConflictException;
import com.example.retail.orders.internal.domain.Order;
import com.example.retail.orders.internal.domain.OrderIdempotency;
import com.example.retail.orders.internal.domain.OrderItem;
import com.example.retail.orders.internal.domain.OrderStatus;
import com.example.retail.orders.internal.persistence.OrderIdempotencyRepository;
import com.example.retail.orders.internal.persistence.OrderRepository;
import com.example.retail.orders.internal.stream.OrderStreamStoreService;
import com.example.retail.payments.PaymentsFacade;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import com.example.retail.shared.orders.Address;
import com.example.retail.shared.orders.OrderConfirmedEvent;
import com.example.retail.shared.orders.OrderItemRequest;
import com.example.retail.shared.orders.OrderRequest;
import com.example.retail.shared.orders.OrderResponse;
import com.example.retail.shared.payments.CustomerResponse;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentResult;
import com.example.retail.shared.payments.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OrdersFacadeImplTest {

    private OrderRepository orderRepository;
    private OrderIdempotencyRepository idempotencyRepository;
    private CatalogFacade catalogFacade;
    private InventoryFacade inventoryFacade;
    private PaymentsFacade paymentsFacade;
    private OrderRequestFingerprinter fingerprinter;
    private ApplicationEventPublisher eventPublisher;
    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private OrderStreamStoreService streamStoreService;

    private OrdersFacadeImpl ordersFacade;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        idempotencyRepository = mock(OrderIdempotencyRepository.class);
        catalogFacade = mock(CatalogFacade.class);
        inventoryFacade = mock(InventoryFacade.class);
        paymentsFacade = mock(PaymentsFacade.class);
        fingerprinter = new OrderRequestFingerprinter();
        eventPublisher = mock(ApplicationEventPublisher.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        streamStoreService = mock(OrderStreamStoreService.class);

        ordersFacade =
                new OrdersFacadeImpl(
                        orderRepository,
                        idempotencyRepository,
                        catalogFacade,
                        inventoryFacade,
                        paymentsFacade,
                        fingerprinter,
                        eventPublisher,
                        rabbitTemplate,
                        objectMapper,
                        streamStoreService);

        lenient()
                .when(idempotencyRepository.save(any(OrderIdempotency.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient()
                .when(idempotencyRepository.saveAndFlush(any(OrderIdempotency.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private CustomerResponse sampleCustomer(Long customerId) {
        return new CustomerResponse(
                customerId,
                "John",
                "john@example.com",
                "+1-555-0100",
                "123 Main St",
                "Apt 4B",
                "New York",
                "NY",
                "10001",
                "USA",
                BigDecimal.valueOf(5000.00),
                BigDecimal.ZERO);
    }

    private OrderRequest sampleRequest() {
        return new OrderRequest(
                101L,
                List.of(
                        new OrderItemRequest("P001", 1, BigDecimal.valueOf(999.99)),
                        new OrderItemRequest("P005", 2, BigDecimal.valueOf(249.99))),
                new Address("123 Main St", "Apt 4B", "New York", "NY", "10001", "USA"));
    }

    @Test
    @DisplayName("Successful order checkout saga coordinates catalog, inventory, and payments")
    void successfulCheckoutSaga() {
        OrderRequest request = sampleRequest();
        String key = "test-key-1";

        // Mocks
        given(idempotencyRepository.findByCustomerIdAndIdempotencyKey(101L, key))
                .willReturn(Optional.empty());
        given(idempotencyRepository.save(any(OrderIdempotency.class)))
                .willAnswer(inv -> inv.getArgument(0));

        CustomerResponse customer = sampleCustomer(101L);
        given(paymentsFacade.findCustomerById(101L)).willReturn(Optional.of(customer));
        given(catalogFacade.existsByProductCode("P001")).willReturn(true);
        given(catalogFacade.existsByProductCode("P005")).willReturn(true);

        Order savedOrder =
                new Order()
                        .setId(1001L)
                        .setCustomerId(101L)
                        .setStatus(OrderStatus.NEW)
                        .setDeliveryAddress(request.deliveryAddress())
                        .setTotalPrice(BigDecimal.valueOf(1499.97))
                        .setCreatedDate(LocalDateTime.now());
        OrderItem item1 =
                new OrderItem()
                        .setId(1L)
                        .setProductCode("P001")
                        .setQuantity(1)
                        .setProductPrice(BigDecimal.valueOf(999.99));
        OrderItem item2 =
                new OrderItem()
                        .setId(2L)
                        .setProductCode("P005")
                        .setQuantity(2)
                        .setProductPrice(BigDecimal.valueOf(249.99));
        savedOrder.addOrderItem(item1);
        savedOrder.addOrderItem(item2);

        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        // Inventory reservation succeeds
        InventoryReservationResult invResult =
                new InventoryReservationResult(
                        key, 1001L, InventoryReservationStatus.RESERVED, "Reserved", List.of());
        given(inventoryFacade.reserve(any(InventoryReservationRequest.class)))
                .willReturn(invResult);

        // Payment reservation succeeds
        PaymentResult payResult =
                new PaymentResult(
                        key,
                        101L,
                        1001L,
                        BigDecimal.valueOf(1499.97),
                        "USD",
                        PaymentStatus.RESERVED,
                        "Reserved");
        given(paymentsFacade.reserve(any(PaymentReservationRequest.class))).willReturn(payResult);

        OrderResponse response = ordersFacade.createOrder(request, key);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(1001L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(1499.97));

        verify(paymentsFacade).confirm(key);
        verify(inventoryFacade).consume(key);
        verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));
    }

    @Test
    @DisplayName("Catalog validation failure aborts before inventory or payment reservations")
    void catalogValidationFailureAborts() {
        OrderRequest request = sampleRequest();
        String key = "test-key-2";

        given(idempotencyRepository.findByCustomerIdAndIdempotencyKey(101L, key))
                .willReturn(Optional.empty());
        given(idempotencyRepository.save(any(OrderIdempotency.class)))
                .willAnswer(inv -> inv.getArgument(0));

        CustomerResponse customer = sampleCustomer(101L);
        given(paymentsFacade.findCustomerById(101L)).willReturn(Optional.of(customer));
        given(catalogFacade.existsByProductCode("P001")).willReturn(true);
        given(catalogFacade.existsByProductCode("P005")).willReturn(false); // missing in catalog

        assertThatThrownBy(() -> ordersFacade.createOrder(request, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P005");

        verify(inventoryFacade, never()).reserve(any());
        verify(paymentsFacade, never()).reserve(any());
    }

    @Test
    @DisplayName(
            "Inventory reservation failure transitions order to FAILED without calling payment")
    void inventoryReservationFailureMarksOrderFailed() {
        OrderRequest request = sampleRequest();
        String key = "test-key-3";

        given(idempotencyRepository.findByCustomerIdAndIdempotencyKey(101L, key))
                .willReturn(Optional.empty());
        given(idempotencyRepository.save(any(OrderIdempotency.class)))
                .willAnswer(inv -> inv.getArgument(0));

        CustomerResponse customer = sampleCustomer(101L);
        given(paymentsFacade.findCustomerById(101L)).willReturn(Optional.of(customer));
        given(catalogFacade.existsByProductCode("P001")).willReturn(true);
        given(catalogFacade.existsByProductCode("P005")).willReturn(true);

        Order savedOrder =
                new Order()
                        .setId(1002L)
                        .setCustomerId(101L)
                        .setStatus(OrderStatus.NEW)
                        .setDeliveryAddress(request.deliveryAddress())
                        .setTotalPrice(BigDecimal.valueOf(1499.97));
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        InventoryReservationResult invResult =
                new InventoryReservationResult(
                        key,
                        1002L,
                        InventoryReservationStatus.FAILED,
                        "Insufficient stock",
                        List.of());
        given(inventoryFacade.reserve(any(InventoryReservationRequest.class)))
                .willReturn(invResult);

        assertThatThrownBy(() -> ordersFacade.createOrder(request, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Inventory reservation failed");

        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(savedOrder.getSource()).isEqualTo("INVENTORY");
        verify(paymentsFacade, never()).reserve(any());
    }

    @Test
    @DisplayName(
            "Payment reservation failure compensates by releasing inventory and marking order FAILED")
    void paymentFailureCompensatesInventory() {
        OrderRequest request = sampleRequest();
        String key = "test-key-4";

        given(idempotencyRepository.findByCustomerIdAndIdempotencyKey(101L, key))
                .willReturn(Optional.empty());
        given(idempotencyRepository.save(any(OrderIdempotency.class)))
                .willAnswer(inv -> inv.getArgument(0));

        CustomerResponse customer = sampleCustomer(101L);
        given(paymentsFacade.findCustomerById(101L)).willReturn(Optional.of(customer));
        given(catalogFacade.existsByProductCode("P001")).willReturn(true);
        given(catalogFacade.existsByProductCode("P005")).willReturn(true);

        Order savedOrder =
                new Order()
                        .setId(1003L)
                        .setCustomerId(101L)
                        .setStatus(OrderStatus.NEW)
                        .setDeliveryAddress(request.deliveryAddress())
                        .setTotalPrice(BigDecimal.valueOf(1499.97));
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        // Inventory succeeds
        InventoryReservationResult invResult =
                new InventoryReservationResult(
                        key, 1003L, InventoryReservationStatus.RESERVED, "Reserved", List.of());
        given(inventoryFacade.reserve(any(InventoryReservationRequest.class)))
                .willReturn(invResult);

        // Payment fails (e.g. insufficient balance)
        PaymentResult payResult =
                new PaymentResult(
                        key,
                        101L,
                        1003L,
                        BigDecimal.valueOf(1499.97),
                        "USD",
                        PaymentStatus.FAILED,
                        "Insufficient funds");
        given(paymentsFacade.reserve(any(PaymentReservationRequest.class))).willReturn(payResult);

        assertThatThrownBy(() -> ordersFacade.createOrder(request, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment reservation failed");

        // Verify compensation
        verify(inventoryFacade).release(key);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(savedOrder.getSource()).isEqualTo("PAYMENT");
    }

    @Test
    @DisplayName("Idempotent replay with matching fingerprint returns original cached response")
    void idempotentReplayReturnsCachedResponse() throws Exception {
        OrderRequest request = sampleRequest();
        String key = "test-key-5";
        String fingerprint = fingerprinter.computeFingerprint(request);

        OrderResponse cachedResponse =
                new OrderResponse(
                        1004L,
                        101L,
                        "CONFIRMED",
                        "WEB",
                        request.deliveryAddress(),
                        LocalDateTime.now(),
                        BigDecimal.valueOf(1499.97),
                        List.of());

        OrderIdempotency existing =
                new OrderIdempotency()
                        .setCustomerId(101L)
                        .setIdempotencyKey(key)
                        .setRequestFingerprint(fingerprint)
                        .setOrderId(1004L)
                        .setStatus(OrderIdempotency.STATUS_COMPLETED)
                        .setResponsePayload(objectMapper.writeValueAsString(cachedResponse));

        given(idempotencyRepository.findByCustomerIdAndIdempotencyKey(101L, key))
                .willReturn(Optional.of(existing));

        OrderResponse response = ordersFacade.createOrder(request, key);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(1004L);
        assertThat(response.status()).isEqualTo("CONFIRMED");

        // No new reservations executed
        verify(inventoryFacade, never()).reserve(any());
        verify(paymentsFacade, never()).reserve(any());
    }

    @Test
    @DisplayName("Idempotency collision with different fingerprint throws HTTP 409 Conflict")
    void idempotencyCollisionThrowsConflict() {
        OrderRequest request = sampleRequest();
        String key = "test-key-6";

        OrderIdempotency existing =
                new OrderIdempotency()
                        .setCustomerId(101L)
                        .setIdempotencyKey(key)
                        .setRequestFingerprint("different-fingerprint-sha256")
                        .setOrderId(1005L)
                        .setStatus(OrderIdempotency.STATUS_COMPLETED);

        given(idempotencyRepository.findByCustomerIdAndIdempotencyKey(101L, key))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> ordersFacade.createOrder(request, key))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different order request");
    }

    @Test
    @DisplayName("Cancelling confirmed order refunds payment and marks order ROLLBACK")
    void cancelConfirmedOrder() {
        Order order =
                new Order()
                        .setId(1006L)
                        .setCustomerId(101L)
                        .setStatus(OrderStatus.CONFIRMED)
                        .setTotalPrice(BigDecimal.valueOf(500.00));

        given(orderRepository.findOrderById(1006L)).willReturn(Optional.of(order));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        ordersFacade.cancelOrder(1006L);

        verify(paymentsFacade).refund(any(PaymentRefundRequest.class));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
    }

    @Test
    @DisplayName(
            "Concurrent insert collision on idempotency table throws IdempotencyConflictException")
    void concurrentInsertCollisionThrowsConflict() {
        OrderRequest request = sampleRequest();
        String key = "test-key-collision";

        given(idempotencyRepository.findByCustomerIdAndIdempotencyKey(101L, key))
                .willReturn(Optional.empty());
        given(idempotencyRepository.saveAndFlush(any(OrderIdempotency.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> ordersFacade.createOrder(request, key))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("already in progress");
    }
}
