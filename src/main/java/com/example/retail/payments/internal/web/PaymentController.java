/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.web;

import com.example.retail.payments.internal.application.PaymentService;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Returns HTTP 200 with the reservation result, including {@code FAILED} for insufficient
     * funds.
     */
    @PostMapping("/reserve")
    public ResponseEntity<PaymentResult> reserve(
            @RequestBody @Valid PaymentReservationRequest request) {
        PaymentResult result = paymentService.reserve(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Confirms the reservation using its original key and returns HTTP 200 with the payment result.
     */
    @PostMapping(value = {"/confirm/{idempotencyKey}", "/{idempotencyKey}/confirm"})
    public ResponseEntity<PaymentResult> confirm(@PathVariable String idempotencyKey) {
        return ResponseEntity.ok(paymentService.confirm(idempotencyKey));
    }

    /**
     * Releases the reservation using its original key and returns HTTP 200 with the payment result.
     */
    @PostMapping(value = {"/release/{idempotencyKey}", "/{idempotencyKey}/release"})
    public ResponseEntity<PaymentResult> release(@PathVariable String idempotencyKey) {
        return ResponseEntity.ok(paymentService.release(idempotencyKey));
    }

    /** Processes the refund and returns HTTP 200 with its stored payment result. */
    @PostMapping("/refund")
    public ResponseEntity<PaymentResult> refund(@RequestBody @Valid PaymentRefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(request));
    }

    /** Returns HTTP 200 with the stored payment result, or HTTP 404 if the key is unknown. */
    @GetMapping(value = {"/{idempotencyKey}", "/status/{idempotencyKey}"})
    public ResponseEntity<PaymentResult> getStatus(@PathVariable String idempotencyKey) {
        return paymentService
                .getPaymentStatus(idempotencyKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
