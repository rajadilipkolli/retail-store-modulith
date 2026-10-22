/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.orders.internal.domain.Order;
import com.example.retail.orders.internal.domain.OrderIdempotency;
import com.example.retail.orders.internal.domain.OrderItem;
import com.example.retail.orders.internal.domain.OrderStatus;
import com.example.retail.orders.internal.persistence.OrderIdempotencyRepository;
import com.example.retail.orders.internal.persistence.OrderRepository;
import com.example.retail.shared.orders.Address;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class OrderPersistenceIT {

    @Autowired private MockMvcTester mockMvcTester;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderIdempotencyRepository idempotencyRepository;

    @Test
    @DisplayName("Sample orders from Liquibase are accessible via /api/v1/orders")
    void sampleOrdersLoadedAndAccessible() {
        var result = mockMvcTester.get().uri("/api/v1/orders");
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(3));
    }

    @Test
    @DisplayName("Dedicated orders schema tables exist and contain valid sample records")
    void ordersSchemaTablesExist() {
        Long orderCount =
                jdbcClient.sql("SELECT count(*) FROM orders.orders").query(Long.class).single();
        Long itemCount =
                jdbcClient
                        .sql("SELECT count(*) FROM orders.order_items")
                        .query(Long.class)
                        .single();

        assertThat(orderCount).isGreaterThanOrEqualTo(3);
        assertThat(itemCount).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Creating order with items cascades and sets Tsid ID")
    void createOrderWithItemsCascades() {
        Order order =
                new Order()
                        .setCustomerId(101L)
                        .setStatus(OrderStatus.NEW)
                        .setSource("WEB")
                        .setDeliveryAddress(
                                new Address(
                                        "123 Main St", "Apt 1", "New York", "NY", "10001", "USA"))
                        .setTotalPrice(BigDecimal.valueOf(100.00))
                        .setCreatedDate(LocalDateTime.now());

        order.addOrderItem(
                new OrderItem()
                        .setProductCode("P001")
                        .setQuantity(1)
                        .setProductPrice(BigDecimal.valueOf(100.00)));

        Order saved = orderRepository.save(order);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getId()).isNotNull();
    }

    @Test
    @DisplayName("OrderIdempotency enforces unique constraint on (customer_id, idempotency_key)")
    void idempotencyUniqueConstraintEnforced() {
        String key = "unique-key-" + System.nanoTime();
        OrderIdempotency record1 =
                new OrderIdempotency()
                        .setCustomerId(101L)
                        .setIdempotencyKey(key)
                        .setRequestFingerprint("fp1")
                        .setStatus(OrderIdempotency.STATUS_PROCESSING)
                        .setCreatedDate(LocalDateTime.now());
        idempotencyRepository.saveAndFlush(record1);

        OrderIdempotency record2 =
                new OrderIdempotency()
                        .setCustomerId(101L)
                        .setIdempotencyKey(key)
                        .setRequestFingerprint("fp2")
                        .setStatus(OrderIdempotency.STATUS_PROCESSING)
                        .setCreatedDate(LocalDateTime.now());

        assertThatThrownBy(() -> idempotencyRepository.saveAndFlush(record2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
