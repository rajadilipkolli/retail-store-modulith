/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.payments.CustomerRequest;
import com.example.retail.shared.payments.CustomerResponse;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentResult;
import java.util.Optional;

/** Public API facade for the Payments module. */
public interface PaymentsFacade {

    boolean validateCustomerEligibility(Long customerId);

    Optional<CustomerResponse> findCustomerById(Long customerId);

    Optional<CustomerResponse> findCustomerByEmail(String email);

    PagedResult<CustomerResponse> findAllCustomers(
            int pageNo, int pageSize, String sortBy, String sortDir);

    CustomerResponse saveCustomer(CustomerRequest customerRequest);

    Optional<CustomerResponse> updateCustomer(Long customerId, CustomerRequest customerRequest);

    void deleteCustomerById(Long customerId);

    PaymentResult reserve(PaymentReservationRequest request);

    PaymentResult confirm(String idempotencyKey);

    PaymentResult release(String idempotencyKey);

    PaymentResult refund(PaymentRefundRequest request);

    Optional<PaymentResult> getPaymentStatus(String idempotencyKey);
}
