/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.retail.orders.internal.domain.Order;
import com.example.retail.orders.internal.domain.OrderItem;
import com.example.retail.orders.internal.domain.OrderStatus;
import com.example.retail.orders.internal.persistence.OrderRepository;
import com.example.retail.orders.internal.stream.OrderStreamStoreService;
import com.example.retail.shared.orders.OrderDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class OrderStreamStoreServiceTest {

    private OrderRepository orderRepository;
    private OrderStreamStoreService streamStoreService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        streamStoreService = new OrderStreamStoreService(orderRepository);
    }

    @Test
    @DisplayName("Put and get order from stream store")
    void putAndGetFromStreamStore() {
        OrderDto dto =
                new OrderDto(
                        10L,
                        101L,
                        "CONFIRMED",
                        "WEB",
                        List.of(
                                new OrderDto.OrderItemDto(
                                        1L, "P001", 1, BigDecimal.valueOf(999.99))));

        streamStoreService.put(dto);

        Optional<OrderDto> result = streamStoreService.getOrderFromStoreById("10");
        assertThat(result).isPresent();
        assertThat(result.get().orderId()).isEqualTo(10L);
        assertThat(result.get().status()).isEqualTo("CONFIRMED");

        List<OrderDto> all = streamStoreService.getAllOrders(0, 10);
        assertThat(all).hasSize(1);
    }

    @Test
    @DisplayName("Stream store falls back to database when store is cold")
    void fallbackToDatabaseWhenCold() {
        Order order =
                new Order()
                        .setId(5L)
                        .setCustomerId(101L)
                        .setStatus(OrderStatus.CONFIRMED)
                        .setSource("WEB");
        order.addOrderItem(
                new OrderItem()
                        .setId(1L)
                        .setProductCode("P001")
                        .setQuantity(2)
                        .setProductPrice(BigDecimal.valueOf(100.00)));

        given(orderRepository.findOrderById(5L)).willReturn(Optional.of(order));
        given(orderRepository.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(order)));
        given(orderRepository.findByIdIn(List.of(5L))).willReturn(List.of(order));

        Optional<OrderDto> result = streamStoreService.getOrderFromStoreById("5");
        assertThat(result).isPresent();
        assertThat(result.get().orderId()).isEqualTo(5L);

        List<OrderDto> all = streamStoreService.getAllOrders(0, 10);
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().orderId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Invalid string ID returns empty optional")
    void invalidIdReturnsEmpty() {
        assertThat(streamStoreService.getOrderFromStoreById("invalid-id")).isEmpty();
    }
}
