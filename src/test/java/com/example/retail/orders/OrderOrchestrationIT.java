/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.inventory.InventoryFacade;
import com.example.retail.orders.internal.domain.Order;
import com.example.retail.orders.internal.domain.OrderStatus;
import com.example.retail.orders.internal.persistence.OrderRepository;
import com.example.retail.payments.PaymentsFacade;
import com.example.retail.shared.inventory.InventoryResponse;
import com.example.retail.shared.payments.CustomerResponse;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class OrderOrchestrationIT {

    @Autowired private MockMvcTester mockMvcTester;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentsFacade paymentsFacade;
    @Autowired private InventoryFacade inventoryFacade;

    @Test
    @DisplayName(
            "End-to-end checkout saga coordinates inventory and payment with successful confirmation")
    void endToEndCheckoutSagaSuccess() {
        CustomerResponse initialCustomer = paymentsFacade.findCustomerById(101L).orElseThrow();
        BigDecimal initialBalance = initialCustomer.amountAvailable();
        InventoryResponse initialInventory =
                inventoryFacade.findInventoryByProductCode("P001").orElseThrow();
        int initialStock = initialInventory.availableQuantity();

        String key = "saga-success-" + System.nanoTime();
        String json =
                """
                {
                    "customerId": 101,
                    "items": [
                        {"productCode": "P001", "quantity": 1, "productPrice": 999.99}
                    ],
                    "deliveryAddress": {
                        "addressLine1": "123 Main St",
                        "city": "New York",
                        "state": "NY",
                        "zipCode": "10001",
                        "country": "USA"
                    }
                }
                """;

        var postAssert =
                assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/orders")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json));

        postAssert.hasStatus(HttpStatus.CREATED);
        java.util.concurrent.atomic.AtomicReference<String> idRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        postAssert.bodyJson().extractingPath("$.orderId").asString().satisfies(idRef::set);
        Long orderId = Long.valueOf(idRef.get());

        // Verify order in database
        Optional<Order> orderOpt = orderRepository.findOrderById(orderId);
        assertThat(orderOpt).isPresent();
        assertThat(orderOpt.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Verify customer balance decreased by $999.99
        CustomerResponse updatedCustomer = paymentsFacade.findCustomerById(101L).orElseThrow();
        assertThat(updatedCustomer.amountAvailable())
                .isEqualByComparingTo(initialBalance.subtract(BigDecimal.valueOf(999.99)));

        // Verify inventory decreased by 1
        InventoryResponse updatedInventory =
                inventoryFacade.findInventoryByProductCode("P001").orElseThrow();
        assertThat(updatedInventory.availableQuantity()).isEqualTo(initialStock - 1);

        // Test identical idempotency replay returns same order without repeating charge
        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/orders")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .bodyJson()
                .extractingPath("$.orderId")
                .asString()
                .isEqualTo(orderId.toString());

        CustomerResponse replayedCustomer = paymentsFacade.findCustomerById(101L).orElseThrow();
        assertThat(replayedCustomer.amountAvailable())
                .isEqualByComparingTo(updatedCustomer.amountAvailable());

        // Test mismatched payload with same key returns 409 Conflict
        String conflictingJson =
                """
                {
                    "customerId": 101,
                    "items": [
                        {"productCode": "P001", "quantity": 2, "productPrice": 999.99}
                    ],
                    "deliveryAddress": {
                        "addressLine1": "123 Main St",
                        "city": "New York",
                        "state": "NY",
                        "zipCode": "10001",
                        "country": "USA"
                    }
                }
                """;

        var conflictResult =
                mockMvcTester
                        .post()
                        .uri("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingJson);

        assertThat(conflictResult).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Payment failure triggers compensation to release reserved inventory")
    void paymentFailureCompensatesInventory() {
        InventoryResponse initialInventory =
                inventoryFacade.findInventoryByProductCode("P005").orElseThrow();
        int initialStock = initialInventory.availableQuantity();

        // Customer 102 balance is 3500.00. We order 20 units of P005 (20 * 249.99 = ~5000.00),
        // which exceeds balance!
        String key = "pay-fail-" + System.nanoTime();
        String json =
                """
                {
                    "customerId": 102,
                    "items": [
                        {"productCode": "P005", "quantity": 20, "productPrice": 249.99}
                    ],
                    "deliveryAddress": {
                        "addressLine1": "456 Oak Ave",
                        "city": "Los Angeles",
                        "state": "CA",
                        "zipCode": "90210",
                        "country": "USA"
                    }
                }
                """;

        var result =
                mockMvcTester
                        .post()
                        .uri("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);

        // Verify inventory was restored/released
        InventoryResponse finalInventory =
                inventoryFacade.findInventoryByProductCode("P005").orElseThrow();
        assertThat(finalInventory.availableQuantity()).isEqualTo(initialStock);
    }
}
