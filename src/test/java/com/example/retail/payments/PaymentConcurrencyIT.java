/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.payments.internal.domain.Customer;
import com.example.retail.payments.internal.domain.InvalidPaymentTransitionException;
import com.example.retail.payments.internal.persistence.CustomerRepository;
import com.example.retail.payments.internal.persistence.PaymentOperationRepository;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentResult;
import com.example.retail.shared.payments.PaymentStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ContainersConfig.class)
class PaymentConcurrencyIT {

    @Autowired private PaymentsFacade paymentsFacade;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PaymentOperationRepository operationRepository;

    @Test
    @DisplayName("Concurrent reservations never overdraft customer balance")
    void concurrentReservations_NeverOverdraft() throws Exception {
        // Customer with exactly $100.00
        Customer customer =
                new Customer(
                        null,
                        "Concurrent User",
                        "concurrent@example.com",
                        "1234567890",
                        "Street",
                        null,
                        "City",
                        "State",
                        "12345",
                        "USA",
                        BigDecimal.valueOf(100.00).setScale(2),
                        BigDecimal.ZERO.setScale(2));
        Customer saved = customerRepository.saveAndFlush(customer);
        Long customerId = saved.getId();

        int numberOfThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<PaymentResult>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            tasks.add(
                    () -> {
                        startLatch.await();
                        PaymentReservationRequest req =
                                new PaymentReservationRequest(
                                        "conc-reserve-" + index,
                                        (long) (2000 + index),
                                        customerId,
                                        BigDecimal.valueOf(10.00),
                                        "USD");
                        return paymentsFacade.reserve(req);
                    });
        }

        startLatch.countDown();
        List<Future<PaymentResult>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        int reservedCount = 0;
        int declinedCount = 0;

        for (Future<PaymentResult> future : futures) {
            PaymentResult result = future.get();
            if (result.status() == PaymentStatus.RESERVED) {
                reservedCount++;
            } else if (result.status() == PaymentStatus.FAILED) {
                declinedCount++;
            }
        }

        assertThat(reservedCount).isEqualTo(10);
        assertThat(declinedCount).isEqualTo(10);

        Customer finalCustomer = customerRepository.findById(customerId).orElseThrow();
        assertThat(finalCustomer.getAmountAvailable()).isEqualByComparingTo("0.00");
        assertThat(finalCustomer.getAmountReserved()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Concurrent requests with the same idempotency key result in exactly one debit")
    void concurrentDuplicateRequests_SameKey() throws Exception {
        Customer customer =
                new Customer(
                        null,
                        "Same Key User",
                        "samekey@example.com",
                        "1234567890",
                        "Street",
                        null,
                        "City",
                        "State",
                        "12345",
                        "USA",
                        BigDecimal.valueOf(100.00).setScale(2),
                        BigDecimal.ZERO.setScale(2));
        Customer saved = customerRepository.saveAndFlush(customer);
        Long customerId = saved.getId();

        int numberOfThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<PaymentResult>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            tasks.add(
                    () -> {
                        startLatch.await();
                        PaymentReservationRequest req =
                                new PaymentReservationRequest(
                                        "same-key-idem-1",
                                        3001L,
                                        customerId,
                                        BigDecimal.valueOf(25.00),
                                        "USD");
                        return paymentsFacade.reserve(req);
                    });
        }

        startLatch.countDown();
        List<Future<PaymentResult>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<PaymentResult> future : futures) {
            PaymentResult result = future.get();
            assertThat(result.status()).isEqualTo(PaymentStatus.RESERVED);
            assertThat(result.amount()).isEqualByComparingTo("25.00");
        }

        Customer finalCustomer = customerRepository.findById(customerId).orElseThrow();
        // $100 - $25 = $75, exactly once
        assertThat(finalCustomer.getAmountAvailable()).isEqualByComparingTo("75.00");
        assertThat(finalCustomer.getAmountReserved()).isEqualByComparingTo("25.00");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentRefundsRespectCaptureAndIdempotency(boolean sameKey) throws Exception {
        String prefix = sameKey ? "duplicate-refund" : "distinct-refunds";
        Customer customer =
                customerRepository.saveAndFlush(
                        new Customer(
                                null,
                                "Refund User",
                                prefix + "@example.com",
                                "1234567890",
                                "Street",
                                null,
                                "City",
                                "State",
                                "12345",
                                "USA",
                                new BigDecimal("100.00"),
                                BigDecimal.ZERO.setScale(2)));
        Long orderId = sameKey ? 4001L : 4002L;
        paymentsFacade.reserve(
                new PaymentReservationRequest(
                        prefix + "-capture",
                        orderId,
                        customer.getId(),
                        new BigDecimal("50.00"),
                        "USD"));
        paymentsFacade.confirm(prefix + "-capture");
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                String key = prefix + "-" + (sameKey ? 0 : i);
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    try {
                                        PaymentResult result =
                                                paymentsFacade.refund(
                                                        new PaymentRefundRequest(
                                                                key,
                                                                orderId,
                                                                customer.getId(),
                                                                new BigDecimal("25.00"),
                                                                "USD",
                                                                "Return"));
                                        assertThat(result.status())
                                                .isEqualTo(PaymentStatus.REFUNDED);
                                        return true;
                                    } catch (InvalidPaymentTransitionException ex) {
                                        return false;
                                    }
                                }));
            }
            boolean allReady = ready.await(30, java.util.concurrent.TimeUnit.SECONDS);
            start.countDown();
            assertThat(allReady).isTrue();
            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            }
            assertThat(successes).isEqualTo(sameKey ? threads : 2);
        }
        Customer persisted = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(persisted.getAmountAvailable())
                .isEqualByComparingTo(sameKey ? "75.00" : "100.00");
        assertThat(persisted.getAmountReserved()).isEqualByComparingTo("0.00");
        assertThat(
                        operationRepository.findByOrderId(orderId).stream()
                                .filter(op -> op.getStatus() == PaymentStatus.REFUNDED)
                                .count())
                .isEqualTo(sameKey ? 1 : 2);
    }
}
