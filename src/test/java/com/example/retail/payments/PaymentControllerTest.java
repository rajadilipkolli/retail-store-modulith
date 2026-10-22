/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.retail.payments.internal.application.PaymentService;
import com.example.retail.payments.internal.domain.CurrencyMismatchException;
import com.example.retail.payments.internal.domain.CustomerNotFoundException;
import com.example.retail.payments.internal.domain.InvalidPaymentTransitionException;
import com.example.retail.payments.internal.domain.PaymentIdempotencyConflictException;
import com.example.retail.payments.internal.domain.PaymentNotFoundException;
import com.example.retail.payments.internal.web.PaymentController;
import com.example.retail.payments.internal.web.PaymentExceptionHandler;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentResult;
import com.example.retail.shared.payments.PaymentStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = PaymentController.class)
@Import(PaymentExceptionHandler.class)
class PaymentControllerTest {

    @Autowired private MockMvcTester mockMvcTester;

    @MockitoBean private PaymentService paymentService;

    private PaymentResult createResult(String key, PaymentStatus status, BigDecimal amount) {
        return new PaymentResult(
                key, 100L, 10L, amount.setScale(2), "USD", status, "Status: " + status);
    }

    @Test
    @DisplayName("POST /api/v1/payments/reserve reserves payment successfully")
    void reservePaymentSuccess() {
        PaymentResult res =
                createResult("key-1", PaymentStatus.RESERVED, BigDecimal.valueOf(50.00));
        given(paymentService.reserve(any(PaymentReservationRequest.class))).willReturn(res);

        String json =
                """
                {
                    "idempotencyKey": "key-1",
                    "orderId": 100,
                    "customerId": 10,
                    "amount": 50.00,
                    "currency": "USD"
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/payments/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("POST /api/v1/payments/reserve returns 409 on idempotency conflict")
    void reservePaymentConflict() {
        given(paymentService.reserve(any(PaymentReservationRequest.class)))
                .willThrow(PaymentIdempotencyConflictException.forKey("key-1"));

        String json =
                """
                {
                    "idempotencyKey": "key-1",
                    "orderId": 100,
                    "customerId": 10,
                    "amount": 50.00,
                    "currency": "USD"
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/payments/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("POST /api/v1/payments/reserve returns 400 on unsupported currency")
    void reservePaymentCurrencyMismatch() {
        given(paymentService.reserve(any(PaymentReservationRequest.class)))
                .willThrow(new CurrencyMismatchException("EUR"));

        String json =
                """
                {
                    "idempotencyKey": "key-1",
                    "orderId": 100,
                    "customerId": 10,
                    "amount": 50.00,
                    "currency": "EUR"
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/payments/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/v1/payments/confirm/{key} confirms payment")
    void confirmPaymentSuccess() {
        PaymentResult res =
                createResult("key-conf", PaymentStatus.CONFIRMED, BigDecimal.valueOf(50.00));
        given(paymentService.confirm("key-conf")).willReturn(res);

        assertThat(mockMvcTester.post().uri("/api/v1/payments/confirm/key-conf"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CONFIRMED");

        // Test standard REST subresource pattern /{key}/confirm
        assertThat(mockMvcTester.post().uri("/api/v1/payments/key-conf/confirm"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("POST /api/v1/payments/confirm/{key} returns 400 on invalid transition")
    void confirmPaymentInvalidTransition() {
        given(paymentService.confirm("key-conf"))
                .willThrow(
                        new InvalidPaymentTransitionException(PaymentStatus.RELEASED, "CONFIRM"));

        assertThat(mockMvcTester.post().uri("/api/v1/payments/confirm/key-conf"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/v1/payments/release/{key} releases payment")
    void releasePaymentSuccess() {
        PaymentResult res =
                createResult("key-rel", PaymentStatus.RELEASED, BigDecimal.valueOf(30.00));
        given(paymentService.release("key-rel")).willReturn(res);

        assertThat(mockMvcTester.post().uri("/api/v1/payments/release/key-rel"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RELEASED");

        // Test standard REST subresource pattern /{key}/release
        assertThat(mockMvcTester.post().uri("/api/v1/payments/key-rel/release"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("POST /api/v1/payments/refund refunds completed payment")
    void refundPaymentSuccess() {
        PaymentResult res =
                createResult("key-orig", PaymentStatus.REFUNDED, BigDecimal.valueOf(50.00));
        given(paymentService.refund(any(PaymentRefundRequest.class))).willReturn(res);

        String json =
                """
                {
                    "idempotencyKey": "key-orig",
                    "orderId": 100,
                    "customerId": 10,
                    "amount": 50.00,
                    "currency": "USD",
                    "reason": "Return item"
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/payments/refund")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("GET /api/v1/payments/{key} returns payment status when found")
    void getPaymentStatusFound() {
        PaymentResult res =
                createResult("key-stat", PaymentStatus.RESERVED, BigDecimal.valueOf(25.00));
        given(paymentService.getPaymentStatus("key-stat")).willReturn(Optional.of(res));

        assertThat(mockMvcTester.get().uri("/api/v1/payments/key-stat"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.idempotencyKey")
                .isEqualTo("key-stat");

        // Also status/{key} works
        assertThat(mockMvcTester.get().uri("/api/v1/payments/status/key-stat"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.idempotencyKey")
                .isEqualTo("key-stat");
    }

    @Test
    @DisplayName("GET /api/v1/payments/{key} returns 404 when not found")
    void getPaymentStatusNotFound() {
        given(paymentService.getPaymentStatus("key-missing")).willReturn(Optional.empty());

        assertThat(mockMvcTester.get().uri("/api/v1/payments/key-missing"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "release"})
    void missingPaymentReturns404(String action) {
        if (action.equals("confirm")) {
            given(paymentService.confirm("missing"))
                    .willThrow(new PaymentNotFoundException("missing"));
        } else {
            given(paymentService.release("missing"))
                    .willThrow(new PaymentNotFoundException("missing"));
        }
        assertThat(mockMvcTester.post().uri("/api/v1/payments/" + action + "/missing"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("https://api.retail.com/errors/payment-not-found");
        assertThat(mockMvcTester.post().uri("/api/v1/payments/missing/" + action))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reserve", "refund", "confirm", "release"})
    void missingCustomerReturns404(String action) {
        String json =
                """
                {"idempotencyKey":"key", "orderId":100, "customerId":999, "amount":10.00, "currency":"USD"}
                """;
        if (action.equals("reserve")) {
            given(paymentService.reserve(any())).willThrow(new CustomerNotFoundException(999L));
        } else if (action.equals("refund")) {
            given(paymentService.refund(any())).willThrow(new CustomerNotFoundException(999L));
        } else if (action.equals("confirm")) {
            given(paymentService.confirm("key")).willThrow(new CustomerNotFoundException(999L));
        } else {
            given(paymentService.release("key")).willThrow(new CustomerNotFoundException(999L));
        }
        String uri =
                "/api/v1/payments/"
                        + action
                        + (action.equals("confirm") || action.equals("release") ? "/key" : "");
        assertThat(
                        mockMvcTester
                                .post()
                                .uri(uri)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("https://api.retail.com/errors/customer-not-found");
    }
}
