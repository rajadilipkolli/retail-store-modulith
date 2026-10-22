/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.orders.internal.domain.IdempotencyConflictException;
import com.example.retail.payments.PaymentsFacade;
import com.example.retail.shared.orders.Address;
import com.example.retail.shared.orders.OrderItemRequest;
import com.example.retail.shared.orders.OrderRequest;
import com.example.retail.shared.orders.OrderResponse;
import com.example.retail.shared.payments.CustomerResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class OrderConcurrencyIT {

    @Autowired private OrdersFacade ordersFacade;
    @Autowired private PaymentsFacade paymentsFacade;

    @Test
    @DisplayName(
            "Concurrent identical requests with same idempotency key do not duplicate orders or charges")
    void concurrentOrdersWithSameKeySafe() throws InterruptedException {
        CustomerResponse customer = paymentsFacade.findCustomerById(103L).orElseThrow();
        BigDecimal initialBalance = customer.amountAvailable();

        String key = "concurrent-key-" + System.nanoTime();
        OrderRequest request =
                new OrderRequest(
                        103L,
                        List.of(new OrderItemRequest("P001", 1, BigDecimal.valueOf(999.99))),
                        new Address("789 Pine Rd", null, "Chicago", "IL", "60601", "USA"));

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        List<OrderResponse> successfulResponses = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            OrderResponse resp = ordersFacade.createOrder(request, key);
                            successfulResponses.add(resp);
                        } catch (Throwable t) {
                            exceptions.add(t);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // At least one request succeeded
        assertThat(successfulResponses).isNotEmpty();
        Long primaryOrderId = successfulResponses.getFirst().orderId();

        // Any other success must have returned the exact same orderId
        for (OrderResponse resp : successfulResponses) {
            assertThat(resp.orderId()).isEqualTo(primaryOrderId);
        }

        // Any exceptions must be IdempotencyConflictException
        for (Throwable t : exceptions) {
            assertThat(t).isInstanceOf(IdempotencyConflictException.class);
        }

        // Customer balance must have been deducted exactly once ($999.99)
        CustomerResponse finalCustomer = paymentsFacade.findCustomerById(103L).orElseThrow();
        assertThat(finalCustomer.amountAvailable())
                .isEqualByComparingTo(initialBalance.subtract(BigDecimal.valueOf(999.99)));
    }
}
