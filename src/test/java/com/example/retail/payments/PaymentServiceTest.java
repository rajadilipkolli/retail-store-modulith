/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.retail.payments.internal.application.PaymentService;
import com.example.retail.payments.internal.domain.CurrencyMismatchException;
import com.example.retail.payments.internal.domain.Customer;
import com.example.retail.payments.internal.domain.CustomerNotFoundException;
import com.example.retail.payments.internal.domain.InvalidPaymentTransitionException;
import com.example.retail.payments.internal.domain.PaymentIdempotencyConflictException;
import com.example.retail.payments.internal.domain.PaymentNotFoundException;
import com.example.retail.payments.internal.domain.PaymentOperation;
import com.example.retail.payments.internal.persistence.CustomerRepository;
import com.example.retail.payments.internal.persistence.PaymentOperationRepository;
import com.example.retail.shared.payments.PaymentProcessedEvent;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentResult;
import com.example.retail.shared.payments.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private PaymentOperationRepository operationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService =
                new PaymentService(customerRepository, operationRepository, eventPublisher);
    }

    private Customer createCustomer(Long id, BigDecimal available, BigDecimal reserved) {
        return new Customer(
                id,
                "Customer " + id,
                "cust" + id + "@example.com",
                "1234567890",
                "Street 1",
                null,
                "City",
                "State",
                "12345",
                "USA",
                available,
                reserved);
    }

    private PaymentOperation createOperation(
            Long id,
            String key,
            Long orderId,
            Long customerId,
            String type,
            BigDecimal amount,
            String currency,
            PaymentStatus status) {
        PaymentOperation op =
                new PaymentOperation(
                        key,
                        orderId,
                        customerId,
                        type,
                        amount,
                        currency,
                        status,
                        null,
                        Instant.now());
        op.setId(id);
        return op;
    }

    @Test
    @DisplayName("Rejects unsupported non-USD currency")
    void rejectsUnsupportedCurrency() {
        PaymentReservationRequest req =
                new PaymentReservationRequest("key-1", 100L, 1L, BigDecimal.valueOf(50.00), "EUR");

        assertThatThrownBy(() -> paymentService.reserve(req))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("EUR");
    }

    @Test
    @DisplayName("Rejects null or non-positive amount")
    void rejectsNonPositiveAmount() {
        PaymentReservationRequest zeroReq =
                new PaymentReservationRequest("key-1", 100L, 1L, BigDecimal.ZERO, "USD");

        assertThatThrownBy(() -> paymentService.reserve(zeroReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");

        PaymentReservationRequest negReq =
                new PaymentReservationRequest("key-1", 100L, 1L, BigDecimal.valueOf(-10.00), "USD");

        assertThatThrownBy(() -> paymentService.reserve(negReq))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rounds excess decimal places to scale 2 using HALF_UP")
    void roundsExcessScaleUsingHalfUp() {
        PaymentReservationRequest req =
                new PaymentReservationRequest(
                        "key-round", 100L, 1L, BigDecimal.valueOf(19.995), "USD");

        Customer customer = createCustomer(1L, BigDecimal.valueOf(100.00), BigDecimal.ZERO);
        given(operationRepository.findByIdempotencyKey("key-round")).willReturn(Optional.empty());
        given(customerRepository.findWithLockById(1L)).willReturn(Optional.of(customer));
        given(operationRepository.save(any(PaymentOperation.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.reserve(req);

        // 19.995 rounds up to 20.00
        assertThat(result.amount()).isEqualByComparingTo("20.00");
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("80.00");
        assertThat(customer.getAmountReserved()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Successfully reserves funds and publishes outbox event")
    void reserveFundsSuccess() {
        PaymentReservationRequest req =
                new PaymentReservationRequest(
                        "key-123", 200L, 10L, BigDecimal.valueOf(45.50), "USD");

        Customer customer =
                createCustomer(10L, BigDecimal.valueOf(100.00), BigDecimal.valueOf(10.00));
        given(operationRepository.findByIdempotencyKey("key-123")).willReturn(Optional.empty());
        given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        given(operationRepository.save(any(PaymentOperation.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.reserve(req);

        assertThat(result.status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(result.amount()).isEqualByComparingTo("45.50");
        assertThat(result.currency()).isEqualTo("USD");

        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("54.50");
        assertThat(customer.getAmountReserved()).isEqualByComparingTo("55.50");

        ArgumentCaptor<PaymentProcessedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().result().status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(eventCaptor.getValue().result().orderId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("Returns FAILED when available balance is insufficient")
    void reserveInsufficientBalance() {
        PaymentReservationRequest req =
                new PaymentReservationRequest(
                        "key-123", 200L, 10L, BigDecimal.valueOf(150.00), "USD");

        Customer customer = createCustomer(10L, BigDecimal.valueOf(50.00), BigDecimal.ZERO);
        given(operationRepository.findByIdempotencyKey("key-123")).willReturn(Optional.empty());
        given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        given(operationRepository.save(any(PaymentOperation.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.reserve(req);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.message()).contains("Insufficient available balance");
        // Balances remain untouched
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("50.00");
        assertThat(customer.getAmountReserved()).isEqualByComparingTo("0.00");

        verify(eventPublisher).publishEvent(any(PaymentProcessedEvent.class));
    }

    @Test
    @DisplayName("Missing reservation customer throws without recording an operation")
    void reserveCustomerNotFound() {
        PaymentReservationRequest req =
                new PaymentReservationRequest(
                        "key-missing", 200L, 99L, new BigDecimal("50.00"), "USD");
        given(customerRepository.findWithLockById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.reserve(req))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("99");
        verify(operationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Idempotent retry with matching params returns existing result")
    void reserveIdempotentMatching() {
        PaymentReservationRequest req =
                new PaymentReservationRequest(
                        "key-idem", 200L, 10L, BigDecimal.valueOf(50.00), "USD");

        PaymentOperation existing =
                createOperation(
                        1L,
                        "key-idem",
                        200L,
                        10L,
                        "RESERVE",
                        BigDecimal.valueOf(50.00).setScale(2),
                        "USD",
                        PaymentStatus.RESERVED);

        given(operationRepository.findByIdempotencyKey("key-idem"))
                .willReturn(Optional.of(existing));

        PaymentResult result = paymentService.reserve(req);

        assertThat(result.status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(result.amount()).isEqualByComparingTo("50.00");
        verify(customerRepository, never()).findWithLockById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName(
            "Idempotency conflict with different params throws PaymentIdempotencyConflictException")
    void reserveIdempotencyConflictThrows() {
        PaymentReservationRequest req =
                new PaymentReservationRequest(
                        "key-conflict", 200L, 10L, BigDecimal.valueOf(99.00), "USD");

        // Existing has different amount (50.00 vs 99.00)
        PaymentOperation existing =
                createOperation(
                        1L,
                        "key-conflict",
                        200L,
                        10L,
                        "RESERVE",
                        BigDecimal.valueOf(50.00).setScale(2),
                        "USD",
                        PaymentStatus.RESERVED);

        given(operationRepository.findByIdempotencyKey("key-conflict"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.reserve(req))
                .isInstanceOf(PaymentIdempotencyConflictException.class)
                .hasMessageContaining("key-conflict");
    }

    @Test
    @DisplayName("Successfully confirms reservation and clears reserved amount")
    void confirmSuccess() {
        PaymentOperation op =
                createOperation(
                        1L,
                        "key-conf",
                        300L,
                        10L,
                        "RESERVE",
                        BigDecimal.valueOf(50.00).setScale(2),
                        "USD",
                        PaymentStatus.RESERVED);

        Customer customer =
                createCustomer(10L, BigDecimal.valueOf(50.00), BigDecimal.valueOf(50.00));

        given(operationRepository.findByIdempotencyKey("key-conf")).willReturn(Optional.of(op));
        given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        given(operationRepository.save(any(PaymentOperation.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.confirm("key-conf");

        assertThat(result.status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(customer.getAmountReserved()).isEqualByComparingTo("0.00");
        verify(eventPublisher).publishEvent(any(PaymentProcessedEvent.class));
    }

    @Test
    @DisplayName("Confirm is idempotent when already CONFIRMED")
    void confirmAlreadyConfirmedIsIdempotent() {
        PaymentOperation op =
                createOperation(
                        1L,
                        "key-conf",
                        300L,
                        10L,
                        "CONFIRM",
                        BigDecimal.valueOf(50.00).setScale(2),
                        "USD",
                        PaymentStatus.CONFIRMED);

        given(operationRepository.findByIdempotencyKey("key-conf")).willReturn(Optional.of(op));

        PaymentResult result = paymentService.confirm("key-conf");

        assertThat(result.status()).isEqualTo(PaymentStatus.CONFIRMED);
        verify(customerRepository, never()).findWithLockById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Confirm throws InvalidPaymentTransitionException if not RESERVED or CONFIRMED")
    void confirmInvalidStatusThrows() {
        PaymentOperation op =
                createOperation(
                        1L,
                        "key-conf",
                        300L,
                        10L,
                        "RELEASE",
                        BigDecimal.valueOf(50.00).setScale(2),
                        "USD",
                        PaymentStatus.RELEASED);

        given(operationRepository.findByIdempotencyKey("key-conf")).willReturn(Optional.of(op));

        assertThatThrownBy(() -> paymentService.confirm("key-conf"))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("RELEASED");
    }

    @Test
    @DisplayName("Confirm throws not found if payment missing")
    void confirmMissingThrows() {
        given(operationRepository.findByIdempotencyKey("key-missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirm("key-missing"))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("key-missing");
    }

    @Test
    @DisplayName("Successfully releases reservation and restores available amount")
    void releaseSuccess() {
        PaymentOperation op =
                createOperation(
                        1L,
                        "key-rel",
                        400L,
                        10L,
                        "RESERVE",
                        BigDecimal.valueOf(30.00).setScale(2),
                        "USD",
                        PaymentStatus.RESERVED);

        Customer customer =
                createCustomer(10L, BigDecimal.valueOf(70.00), BigDecimal.valueOf(30.00));

        given(operationRepository.findByIdempotencyKey("key-rel")).willReturn(Optional.of(op));
        given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        given(operationRepository.save(any(PaymentOperation.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.release("key-rel");

        assertThat(result.status()).isEqualTo(PaymentStatus.RELEASED);
        assertThat(customer.getAmountReserved()).isEqualByComparingTo("0.00");
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("100.00");
        verify(eventPublisher).publishEvent(any(PaymentProcessedEvent.class));
    }

    @Test
    @DisplayName("Release is idempotent when already RELEASED")
    void releaseAlreadyReleasedIsIdempotent() {
        PaymentOperation op =
                createOperation(
                        1L,
                        "key-rel",
                        400L,
                        10L,
                        "RELEASE",
                        BigDecimal.valueOf(30.00).setScale(2),
                        "USD",
                        PaymentStatus.RELEASED);

        given(operationRepository.findByIdempotencyKey("key-rel")).willReturn(Optional.of(op));

        PaymentResult result = paymentService.release("key-rel");

        assertThat(result.status()).isEqualTo(PaymentStatus.RELEASED);
        verify(customerRepository, never()).findWithLockById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Release throws InvalidPaymentTransitionException if not RESERVED or RELEASED")
    void releaseInvalidStatusThrows() {
        PaymentOperation op =
                createOperation(
                        1L,
                        "key-rel",
                        400L,
                        10L,
                        "CONFIRM",
                        BigDecimal.valueOf(30.00).setScale(2),
                        "USD",
                        PaymentStatus.CONFIRMED);

        given(operationRepository.findByIdempotencyKey("key-rel")).willReturn(Optional.of(op));

        assertThatThrownBy(() -> paymentService.release("key-rel"))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    @DisplayName("Successfully refunds completed payment and credits available balance")
    void refundSuccess() {
        PaymentRefundRequest req =
                new PaymentRefundRequest(
                        "key-refund", 500L, 10L, BigDecimal.valueOf(50.00), "USD", "Return item");

        Customer customer = createCustomer(10L, BigDecimal.valueOf(50.00), BigDecimal.ZERO);

        given(operationRepository.findByIdempotencyKey("key-refund")).willReturn(Optional.empty());
        given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        given(operationRepository.findByOrderId(500L))
                .willReturn(
                        List.of(
                                createOperation(
                                        2L,
                                        "capture",
                                        500L,
                                        10L,
                                        "RESERVE",
                                        new BigDecimal("50.00"),
                                        "USD",
                                        PaymentStatus.CONFIRMED)));
        given(operationRepository.save(any(PaymentOperation.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.refund(req);

        assertThat(result.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("100.00");
        verify(eventPublisher).publishEvent(any(PaymentProcessedEvent.class));
    }

    @Test
    @DisplayName("Refund idempotent when already processed")
    void refundAlreadyProcessedIsIdempotent() {
        PaymentRefundRequest req =
                new PaymentRefundRequest(
                        "key-refund", 500L, 10L, BigDecimal.valueOf(50.00), "USD", "Return item");

        PaymentOperation op =
                createOperation(
                        1L,
                        "key-refund",
                        500L,
                        10L,
                        "REFUND",
                        BigDecimal.valueOf(50.00).setScale(2),
                        "USD",
                        PaymentStatus.REFUNDED);

        given(operationRepository.findByIdempotencyKey("key-refund")).willReturn(Optional.of(op));

        PaymentResult result = paymentService.refund(req);

        assertThat(result.status()).isEqualTo(PaymentStatus.REFUNDED);
        verify(customerRepository, never()).findWithLockById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Refund throws customer not found if customer missing")
    void refundMissingCustomerThrows() {
        PaymentRefundRequest req =
                new PaymentRefundRequest(
                        "key-refund", 500L, 999L, BigDecimal.valueOf(50.00), "USD", "Return item");

        given(operationRepository.findByIdempotencyKey("key-refund")).willReturn(Optional.empty());
        given(customerRepository.findWithLockById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refund(req))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("getPaymentStatus returns result when present")
    void getPaymentStatusFound() {
        PaymentOperation op =
                createOperation(
                        1L,
                        "key-status",
                        600L,
                        10L,
                        "RESERVE",
                        BigDecimal.valueOf(75.00).setScale(2),
                        "USD",
                        PaymentStatus.RESERVED);

        given(operationRepository.findByIdempotencyKey("key-status")).willReturn(Optional.of(op));

        Optional<PaymentResult> result = paymentService.getPaymentStatus("key-status");

        assertThat(result).isPresent();
        assertThat(result.get().idempotencyKey()).isEqualTo("key-status");
        assertThat(result.get().status()).isEqualTo(PaymentStatus.RESERVED);
    }

    @Test
    @DisplayName("getPaymentStatus returns empty when not found")
    void getPaymentStatusNotFound() {
        given(operationRepository.findByIdempotencyKey("key-missing")).willReturn(Optional.empty());

        Optional<PaymentResult> result = paymentService.getPaymentStatus("key-missing");

        assertThat(result).isEmpty();
    }

    static Stream<Arguments> missingCurrencies() {
        return Stream.of((String) null, "", " ", "\t")
                .flatMap(
                        currency ->
                                Stream.of(
                                        Arguments.of(false, currency),
                                        Arguments.of(true, currency)));
    }

    @ParameterizedTest
    @MethodSource("missingCurrencies")
    void missingCurrencyRejectedBeforeRepositoryAccess(boolean refund, String currency) {
        assertThatThrownBy(
                        () -> {
                            if (refund) {
                                paymentService.refund(
                                        new PaymentRefundRequest(
                                                "key", 500L, 10L, BigDecimal.TEN, currency, null));
                            } else {
                                paymentService.reserve(
                                        new PaymentReservationRequest(
                                                "key", 500L, 10L, BigDecimal.TEN, currency));
                            }
                        })
                .isInstanceOf(CurrencyMismatchException.class);
        verifyNoInteractions(customerRepository, operationRepository, eventPublisher);
    }

    static Stream<Arguments> idempotencyMismatches() {
        return Stream.of(false, true)
                .flatMap(
                        refund ->
                                Stream.of(false, true)
                                        .flatMap(
                                                afterLock ->
                                                        Stream.of(
                                                                        "type",
                                                                        "order",
                                                                        "customer",
                                                                        "amount",
                                                                        "currency")
                                                                .map(
                                                                        field ->
                                                                                Arguments.of(
                                                                                        refund,
                                                                                        afterLock,
                                                                                        field))));
    }

    @ParameterizedTest
    @MethodSource("idempotencyMismatches")
    void rejectsMismatchedIdempotencyParameters(boolean refund, boolean afterLock, String field) {
        PaymentOperation existing =
                createOperation(
                        1L,
                        "key",
                        500L,
                        10L,
                        refund ? "REFUND" : "RESERVE",
                        new BigDecimal("10.00"),
                        "USD",
                        refund ? PaymentStatus.REFUNDED : PaymentStatus.RESERVED);
        switch (field) {
            case "type" -> existing.setOperationType(refund ? "RESERVE" : "REFUND");
            case "order" -> existing.setOrderId(501L);
            case "customer" -> existing.setCustomerId(11L);
            case "amount" -> existing.setAmount(new BigDecimal("10.01"));
            case "currency" -> existing.setCurrency("EUR");
        }
        Customer customer = createCustomer(10L, new BigDecimal("50.00"), BigDecimal.ZERO);
        if (afterLock) {
            given(operationRepository.findByIdempotencyKey("key"))
                    .willReturn(Optional.empty(), Optional.of(existing));
            given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        } else {
            given(operationRepository.findByIdempotencyKey("key"))
                    .willReturn(Optional.of(existing));
        }
        assertThatThrownBy(
                        () -> {
                            if (refund) {
                                paymentService.refund(
                                        new PaymentRefundRequest(
                                                "key", 500L, 10L, BigDecimal.TEN, "USD", null));
                            } else {
                                paymentService.reserve(
                                        new PaymentReservationRequest(
                                                "key", 500L, 10L, BigDecimal.TEN, "USD"));
                            }
                        })
                .isInstanceOf(PaymentIdempotencyConflictException.class)
                .hasMessageContaining("key");
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("50.00");
        verify(customerRepository, never()).save(any());
        verify(operationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingCustomerForConfirmOrRelease(boolean confirm) {
        PaymentOperation existing =
                createOperation(
                        1L,
                        "key",
                        500L,
                        10L,
                        "RESERVE",
                        BigDecimal.TEN,
                        "USD",
                        PaymentStatus.RESERVED);
        given(operationRepository.findByIdempotencyKey("key")).willReturn(Optional.of(existing));
        assertThatThrownBy(
                        () -> {
                            if (confirm) paymentService.confirm("key");
                            else paymentService.release("key");
                        })
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("10");
        verify(operationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseMissingPaymentThrows() {
        assertThatThrownBy(() -> paymentService.release("missing"))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "reserved",
                "released",
                "failed",
                "otherCustomer",
                "overLimit",
                "wrongCurrency"
            })
    void refundRejectsInvalidCaptureOrExcessTotalWithoutMutation(String scenario) {
        Customer customer = createCustomer(10L, new BigDecimal("50.00"), BigDecimal.ZERO);
        given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        PaymentStatus status =
                switch (scenario) {
                    case "reserved" -> PaymentStatus.RESERVED;
                    case "released" -> PaymentStatus.RELEASED;
                    case "failed" -> PaymentStatus.FAILED;
                    default -> PaymentStatus.CONFIRMED;
                };
        PaymentOperation capture =
                createOperation(
                        1L,
                        "capture",
                        500L,
                        scenario.equals("otherCustomer") ? 11L : 10L,
                        "RESERVE",
                        new BigDecimal("50.00"),
                        scenario.equals("wrongCurrency") ? "EUR" : "USD",
                        status);
        PaymentOperation priorRefund =
                createOperation(
                        2L,
                        "priorRefund",
                        500L,
                        10L,
                        "REFUND",
                        new BigDecimal("40.00"),
                        "USD",
                        PaymentStatus.REFUNDED);
        given(operationRepository.findByOrderId(500L))
                .willReturn(scenario.equals("missing") ? List.of() : List.of(capture, priorRefund));
        assertThatThrownBy(
                        () ->
                                paymentService.refund(
                                        new PaymentRefundRequest(
                                                "key",
                                                500L,
                                                10L,
                                                new BigDecimal("10.005"),
                                                "USD",
                                                null)))
                .isInstanceOf(
                        scenario.equals("wrongCurrency")
                                ? CurrencyMismatchException.class
                                : InvalidPaymentTransitionException.class);
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("50.00");
        verify(customerRepository, never()).save(any());
        verify(operationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void partialRefundAtCaptureLimitUsesNormalizedAmountAndIgnoresOtherCustomers() {
        Customer customer = createCustomer(10L, new BigDecimal("50.00"), BigDecimal.ZERO);
        given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        given(operationRepository.findByOrderId(500L))
                .willReturn(
                        List.of(
                                createOperation(
                                        1L,
                                        "capture",
                                        500L,
                                        10L,
                                        "RESERVE",
                                        new BigDecimal("50.00"),
                                        "USD",
                                        PaymentStatus.CONFIRMED),
                                createOperation(
                                        2L,
                                        "prior",
                                        500L,
                                        10L,
                                        "REFUND",
                                        new BigDecimal("40.00"),
                                        "USD",
                                        PaymentStatus.REFUNDED),
                                createOperation(
                                        3L,
                                        "other",
                                        500L,
                                        11L,
                                        "REFUND",
                                        new BigDecimal("50.00"),
                                        "USD",
                                        PaymentStatus.REFUNDED)));
        given(operationRepository.save(any(PaymentOperation.class)))
                .willAnswer(inv -> inv.getArgument(0));
        PaymentResult result =
                paymentService.refund(
                        new PaymentRefundRequest(
                                "key", 500L, 10L, new BigDecimal("9.995"), "usd", null));
        assertThat(result.amount()).isEqualByComparingTo("10.00");
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("60.00");
        var ordered = org.mockito.Mockito.inOrder(customerRepository, operationRepository);
        ordered.verify(operationRepository).findByIdempotencyKey("key");
        ordered.verify(customerRepository).findWithLockById(10L);
        ordered.verify(operationRepository).findByIdempotencyKey("key");
        ordered.verify(operationRepository).findByOrderId(500L);
        ordered.verify(customerRepository).save(customer);
    }

    @Test
    void refundMatchingRetryAfterLockDoesNotCreditAgain() {
        Customer customer = createCustomer(10L, new BigDecimal("50.00"), BigDecimal.ZERO);
        PaymentOperation existing =
                createOperation(
                        1L,
                        "key",
                        500L,
                        10L,
                        "REFUND",
                        new BigDecimal("10.00"),
                        "USD",
                        PaymentStatus.REFUNDED);
        given(operationRepository.findByIdempotencyKey("key"))
                .willReturn(Optional.empty(), Optional.of(existing));
        given(customerRepository.findWithLockById(10L)).willReturn(Optional.of(customer));
        PaymentResult result =
                paymentService.refund(
                        new PaymentRefundRequest(
                                "key",
                                500L,
                                10L,
                                new BigDecimal("9.995"),
                                "usd",
                                "different reason"));
        assertThat(result.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo("50.00");
        verify(customerRepository, never()).save(any());
        verify(operationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
