/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.application;

import com.example.retail.payments.PaymentsFacade;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.payments.CustomerRequest;
import com.example.retail.shared.payments.CustomerResponse;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PaymentsFacadeImpl implements PaymentsFacade {

    private final CustomerService customerService;
    private final PaymentService paymentService;

    public PaymentsFacadeImpl(CustomerService customerService, PaymentService paymentService) {
        this.customerService = customerService;
        this.paymentService = paymentService;
    }

    @Override
    public boolean validateCustomerEligibility(Long customerId) {
        return customerService.validateCustomerEligibility(customerId);
    }

    @Override
    public Optional<CustomerResponse> findCustomerById(Long customerId) {
        return customerService.findCustomerById(customerId);
    }

    @Override
    public Optional<CustomerResponse> findCustomerByEmail(String email) {
        return customerService.findCustomerByEmail(email);
    }

    @Override
    public PagedResult<CustomerResponse> findAllCustomers(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        return customerService.findAllCustomers(pageNo, pageSize, sortBy, sortDir);
    }

    @Override
    public CustomerResponse saveCustomer(CustomerRequest customerRequest) {
        return customerService.saveCustomer(customerRequest);
    }

    @Override
    public Optional<CustomerResponse> updateCustomer(
            Long customerId, CustomerRequest customerRequest) {
        return customerService.updateCustomer(customerId, customerRequest);
    }

    @Override
    public void deleteCustomerById(Long customerId) {
        customerService.deleteCustomerById(customerId);
    }

    @Override
    public PaymentResult reserve(PaymentReservationRequest request) {
        return paymentService.reserve(request);
    }

    @Override
    public PaymentResult confirm(String idempotencyKey) {
        return paymentService.confirm(idempotencyKey);
    }

    @Override
    public PaymentResult release(String idempotencyKey) {
        return paymentService.release(idempotencyKey);
    }

    @Override
    public PaymentResult refund(PaymentRefundRequest request) {
        return paymentService.refund(request);
    }

    @Override
    public Optional<PaymentResult> getPaymentStatus(String idempotencyKey) {
        return paymentService.getPaymentStatus(idempotencyKey);
    }
}
