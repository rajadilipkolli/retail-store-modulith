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

    /** Returns true only when the customer exists and has a strictly positive available balance. */
    boolean validateCustomerEligibility(Long customerId);

    /** Returns customer details, or empty if the ID is unknown. */
    Optional<CustomerResponse> findCustomerById(Long customerId);

    /** Returns customer details for the supplied email, or empty if no customer matches. */
    Optional<CustomerResponse> findCustomerByEmail(String email);

    /**
     * Returns a sorted customer page with navigation metadata.
     *
     * @param pageNo zero-based page index
     * @param pageSize positive number of customers per page
     * @param sortBy customer property to sort by
     * @param sortDir {@code asc}, ignoring case, selects ascending order; other values select
     *     descending
     * @throws IllegalArgumentException if the page index is negative, the page size is not
     *     positive, or the sort property is null or empty
     */
    PagedResult<CustomerResponse> findAllCustomers(
            int pageNo, int pageSize, String sortBy, String sortDir);

    /**
     * Creates a customer with zero reserved funds and returns the saved details. Available funds
     * are rounded to two decimal places using {@code HALF_UP}, defaulting to zero when omitted.
     * Duplicate email and persistence errors propagate to the caller.
     */
    CustomerResponse saveCustomer(CustomerRequest customerRequest);

    /**
     * Replaces customer details while preserving reserved funds. A null available amount preserves
     * that balance; a supplied amount replaces it after rounding to two decimal places using {@code
     * HALF_UP}. Returns empty for an unknown ID; duplicate email and persistence errors propagate
     * to the caller.
     */
    Optional<CustomerResponse> updateCustomer(Long customerId, CustomerRequest customerRequest);

    /**
     * Deletes the customer; a missing customer or persistence constraint violation raises an error.
     */
    void deleteCustomerById(Long customerId);

    /**
     * Reserves USD funds and publishes the recorded outcome, returning {@code FAILED} for
     * insufficient balance. Amounts are rounded to two decimal places using {@code HALF_UP}. A
     * matching operation key returns its current result; conflicting parameters are rejected.
     * Missing customers, unsupported currencies, null or nonpositive amounts, and unrecovered
     * persistence errors propagate to the caller.
     */
    PaymentResult reserve(PaymentReservationRequest request);

    /**
     * Captures the reservation identified by its original key and publishes the result. An already
     * confirmed payment returns its stored result without another event or balance change. Unknown
     * keys, missing customers, invalid statuses, and persistence errors propagate to the caller.
     */
    PaymentResult confirm(String idempotencyKey);

    /**
     * Restores reserved funds to the available balance using the original reservation key and
     * publishes the result. An already released payment returns its stored result without another
     * event or balance change. Unknown keys, missing customers, invalid statuses, and persistence
     * errors propagate to the caller.
     */
    PaymentResult release(String idempotencyKey);

    /**
     * Credits a USD refund and publishes the result. Amounts are rounded to two decimal places
     * using {@code HALF_UP}; cumulative refunds cannot exceed confirmed payments for the same order
     * and customer. A matching key returns its stored result, ignoring changes to the reason.
     * Conflicting parameters, invalid amounts or currencies, missing customers, invalid refunds,
     * and unrecovered persistence errors propagate to the caller.
     */
    PaymentResult refund(PaymentRefundRequest request);

    /** Returns the stored payment result for the key, or empty if the key is unknown. */
    Optional<PaymentResult> getPaymentStatus(String idempotencyKey);
}
