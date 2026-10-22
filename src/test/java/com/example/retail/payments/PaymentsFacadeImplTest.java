/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.retail.payments.internal.application.CustomerService;
import com.example.retail.payments.internal.application.PaymentService;
import com.example.retail.payments.internal.application.PaymentsFacadeImpl;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.payments.CustomerRequest;
import com.example.retail.shared.payments.CustomerResponse;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentResult;
import com.example.retail.shared.payments.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class PaymentsFacadeImplTest {

    @Mock private CustomerService customerService;
    @Mock private PaymentService paymentService;

    private PaymentsFacade paymentsFacade;

    @BeforeEach
    void setUp() {
        paymentsFacade = new PaymentsFacadeImpl(customerService, paymentService);
    }

    @Test
    @DisplayName("Delegates validateCustomerEligibility to CustomerService")
    void testValidateCustomerEligibility() {
        given(customerService.validateCustomerEligibility(10L)).willReturn(true);

        assertThat(paymentsFacade.validateCustomerEligibility(10L)).isTrue();
        verify(customerService).validateCustomerEligibility(10L);
    }

    @Test
    @DisplayName("Delegates findCustomerById to CustomerService")
    void testFindCustomerById() {
        CustomerResponse res =
                new CustomerResponse(
                        10L,
                        "John",
                        "john@example.com",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.ZERO);
        given(customerService.findCustomerById(10L)).willReturn(Optional.of(res));

        assertThat(paymentsFacade.findCustomerById(10L)).contains(res);
    }

    @Test
    @DisplayName("Delegates findCustomerByEmail to CustomerService")
    void testFindCustomerByEmail() {
        CustomerResponse res =
                new CustomerResponse(
                        10L,
                        "John",
                        "john@example.com",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.ZERO);
        given(customerService.findCustomerByEmail("john@example.com")).willReturn(Optional.of(res));

        assertThat(paymentsFacade.findCustomerByEmail("john@example.com")).contains(res);
    }

    @Test
    @DisplayName("Delegates findAllCustomers to CustomerService")
    void testFindAllCustomers() {
        PagedResult<CustomerResponse> paged = new PagedResult<>(new PageImpl<>(List.of()));
        given(customerService.findAllCustomers(0, 10, "id", "asc")).willReturn(paged);

        assertThat(paymentsFacade.findAllCustomers(0, 10, "id", "asc")).isEqualTo(paged);
    }

    @Test
    @DisplayName("Delegates saveCustomer to CustomerService")
    void testSaveCustomer() {
        CustomerRequest req =
                new CustomerRequest(
                        "John",
                        "john@example.com",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.TEN);
        CustomerResponse res =
                new CustomerResponse(
                        10L,
                        "John",
                        "john@example.com",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.ZERO);
        given(customerService.saveCustomer(req)).willReturn(res);

        assertThat(paymentsFacade.saveCustomer(req)).isEqualTo(res);
    }

    @Test
    @DisplayName("Delegates updateCustomer to CustomerService")
    void testUpdateCustomer() {
        CustomerRequest req =
                new CustomerRequest(
                        "John",
                        "john@example.com",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.TEN);
        CustomerResponse res =
                new CustomerResponse(
                        10L,
                        "John",
                        "john@example.com",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.ZERO);
        given(customerService.updateCustomer(10L, req)).willReturn(Optional.of(res));

        assertThat(paymentsFacade.updateCustomer(10L, req)).contains(res);
    }

    @Test
    @DisplayName("Delegates deleteCustomerById to CustomerService")
    void testDeleteCustomerById() {
        paymentsFacade.deleteCustomerById(10L);
        verify(customerService).deleteCustomerById(10L);
    }

    @Test
    @DisplayName("Delegates reserve to PaymentService")
    void testReserve() {
        PaymentReservationRequest req =
                new PaymentReservationRequest("key-1", 100L, 10L, BigDecimal.TEN, "USD");
        PaymentResult res =
                new PaymentResult(
                        "key-1",
                        100L,
                        10L,
                        BigDecimal.TEN,
                        "USD",
                        PaymentStatus.RESERVED,
                        "Reserved");
        given(paymentService.reserve(req)).willReturn(res);

        assertThat(paymentsFacade.reserve(req)).isEqualTo(res);
    }

    @Test
    @DisplayName("Delegates confirm to PaymentService")
    void testConfirm() {
        PaymentResult res =
                new PaymentResult(
                        "key-1",
                        100L,
                        10L,
                        BigDecimal.TEN,
                        "USD",
                        PaymentStatus.CONFIRMED,
                        "Confirmed");
        given(paymentService.confirm("key-1")).willReturn(res);

        assertThat(paymentsFacade.confirm("key-1")).isEqualTo(res);
    }

    @Test
    @DisplayName("Delegates release to PaymentService")
    void testRelease() {
        PaymentResult res =
                new PaymentResult(
                        "key-1",
                        100L,
                        10L,
                        BigDecimal.TEN,
                        "USD",
                        PaymentStatus.RELEASED,
                        "Released");
        given(paymentService.release("key-1")).willReturn(res);

        assertThat(paymentsFacade.release("key-1")).isEqualTo(res);
    }

    @Test
    @DisplayName("Delegates refund to PaymentService")
    void testRefund() {
        PaymentRefundRequest req =
                new PaymentRefundRequest("key-1", 100L, 10L, BigDecimal.TEN, "USD", "Return");
        PaymentResult res =
                new PaymentResult(
                        "key-1",
                        100L,
                        10L,
                        BigDecimal.TEN,
                        "USD",
                        PaymentStatus.REFUNDED,
                        "Refunded");
        given(paymentService.refund(req)).willReturn(res);

        assertThat(paymentsFacade.refund(req)).isEqualTo(res);
    }

    @Test
    @DisplayName("Delegates getPaymentStatus to PaymentService")
    void testGetPaymentStatus() {
        PaymentResult res =
                new PaymentResult(
                        "key-1",
                        100L,
                        10L,
                        BigDecimal.TEN,
                        "USD",
                        PaymentStatus.RESERVED,
                        "Reserved");
        given(paymentService.getPaymentStatus("key-1")).willReturn(Optional.of(res));

        assertThat(paymentsFacade.getPaymentStatus("key-1")).contains(res);
    }
}
