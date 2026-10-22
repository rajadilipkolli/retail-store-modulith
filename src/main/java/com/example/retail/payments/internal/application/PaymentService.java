/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.application;

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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentService.class);
    private static final String SUPPORTED_CURRENCY = "USD";

    private final CustomerRepository customerRepository;
    private final PaymentOperationRepository operationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(
            CustomerRepository customerRepository,
            PaymentOperationRepository operationRepository,
            ApplicationEventPublisher eventPublisher) {
        this.customerRepository = customerRepository;
        this.operationRepository = operationRepository;
        this.eventPublisher = eventPublisher;
    }

    public PaymentResult reserve(PaymentReservationRequest request) {
        LOGGER.info(
                "Processing payment reservation for orderId: {}, customerId: {}",
                request.orderId(),
                request.customerId());

        validateCurrency(request.currency());
        BigDecimal normalizedAmount = normalizeAmount(request.amount());

        // Step 1: Idempotency check
        Optional<PaymentOperation> existingOpt =
                operationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOpt.isPresent()) {
            PaymentOperation existing = existingOpt.get();
            validateIdempotencyParameters(
                    existing,
                    request.idempotencyKey(),
                    "RESERVE",
                    request.orderId(),
                    request.customerId(),
                    normalizedAmount,
                    request.currency());
            LOGGER.info(
                    "Returning idempotent existing payment result for key: {}, status: {}",
                    request.idempotencyKey(),
                    existing.getStatus());
            return mapToPaymentResult(existing);
        }

        // Step 2: Pessimistic write lock on Customer
        Optional<Customer> customerOpt = customerRepository.findWithLockById(request.customerId());
        if (customerOpt.isEmpty()) {
            LOGGER.warn(
                    "Payment reservation failed: customer {} not found for order {}",
                    request.customerId(),
                    request.orderId());
            throw new CustomerNotFoundException(request.customerId());
        }

        // A concurrent retry may have committed while waiting for customer lock.
        existingOpt = operationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOpt.isPresent()) {
            PaymentOperation existing = existingOpt.get();
            validateIdempotencyParameters(
                    existing,
                    request.idempotencyKey(),
                    "RESERVE",
                    request.orderId(),
                    request.customerId(),
                    normalizedAmount,
                    request.currency());
            LOGGER.info(
                    "Returning idempotent existing payment result for key: {}, status: {}",
                    request.idempotencyKey(),
                    existing.getStatus());
            return mapToPaymentResult(existing);
        }

        Customer customer = customerOpt.get();

        // Step 3: Check balance
        if (customer.getAmountAvailable().compareTo(normalizedAmount) < 0) {
            LOGGER.warn(
                    "Payment reservation failed: insufficient balance for customer {}, available: {}, requested: {}",
                    customer.getId(),
                    customer.getAmountAvailable(),
                    normalizedAmount);
            return recordAndPublishOperation(
                    request.idempotencyKey(),
                    request.orderId(),
                    customer.getId(),
                    "RESERVE",
                    normalizedAmount,
                    SUPPORTED_CURRENCY,
                    PaymentStatus.FAILED,
                    "Insufficient available balance");
        }

        // Step 4: Mutate balances atomically
        customer.setAmountAvailable(customer.getAmountAvailable().subtract(normalizedAmount));
        customer.setAmountReserved(customer.getAmountReserved().add(normalizedAmount));
        customerRepository.save(customer);

        // Step 5: Record operation and publish outbox event
        return recordAndPublishOperation(
                request.idempotencyKey(),
                request.orderId(),
                customer.getId(),
                "RESERVE",
                normalizedAmount,
                SUPPORTED_CURRENCY,
                PaymentStatus.RESERVED,
                "Payment reserved successfully");
    }

    public PaymentResult confirm(String idempotencyKey) {
        LOGGER.info("Processing payment confirmation for key: {}", idempotencyKey);
        PaymentOperation operation =
                operationRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new PaymentNotFoundException(idempotencyKey));

        if (operation.getStatus() == PaymentStatus.CONFIRMED) {
            LOGGER.info("Payment {} already confirmed (idempotent)", idempotencyKey);
            return mapToPaymentResult(operation);
        }

        if (operation.getStatus() != PaymentStatus.RESERVED) {
            throw InvalidPaymentTransitionException.forTransition(operation.getStatus(), "CONFIRM");
        }

        Customer customer =
                customerRepository
                        .findWithLockById(operation.getCustomerId())
                        .orElseThrow(
                                () -> new CustomerNotFoundException(operation.getCustomerId()));

        customer.setAmountReserved(
                customer.getAmountReserved().subtract(operation.getAmount()).max(BigDecimal.ZERO));
        customerRepository.save(customer);

        operation.setStatus(PaymentStatus.CONFIRMED);
        operation.setUpdatedAt(Instant.now());
        operation.setMessage("Payment confirmed and captured successfully");
        PaymentOperation saved = operationRepository.save(operation);

        PaymentResult result = mapToPaymentResult(saved);
        eventPublisher.publishEvent(new PaymentProcessedEvent(result));
        return result;
    }

    public PaymentResult release(String idempotencyKey) {
        LOGGER.info("Processing payment release for key: {}", idempotencyKey);
        PaymentOperation operation =
                operationRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new PaymentNotFoundException(idempotencyKey));

        if (operation.getStatus() == PaymentStatus.RELEASED) {
            LOGGER.info("Payment {} already released (idempotent)", idempotencyKey);
            return mapToPaymentResult(operation);
        }

        if (operation.getStatus() != PaymentStatus.RESERVED) {
            throw InvalidPaymentTransitionException.forTransition(operation.getStatus(), "RELEASE");
        }

        Customer customer =
                customerRepository
                        .findWithLockById(operation.getCustomerId())
                        .orElseThrow(
                                () -> new CustomerNotFoundException(operation.getCustomerId()));

        customer.setAmountReserved(
                customer.getAmountReserved().subtract(operation.getAmount()).max(BigDecimal.ZERO));
        customer.setAmountAvailable(customer.getAmountAvailable().add(operation.getAmount()));
        customerRepository.save(customer);

        operation.setStatus(PaymentStatus.RELEASED);
        operation.setUpdatedAt(Instant.now());
        operation.setMessage("Payment reservation released successfully");
        PaymentOperation saved = operationRepository.save(operation);

        PaymentResult result = mapToPaymentResult(saved);
        eventPublisher.publishEvent(new PaymentProcessedEvent(result));
        return result;
    }

    public PaymentResult refund(PaymentRefundRequest request) {
        LOGGER.info(
                "Processing payment refund for orderId: {}, customerId: {}",
                request.orderId(),
                request.customerId());

        validateCurrency(request.currency());
        BigDecimal normalizedAmount = normalizeAmount(request.amount());

        Optional<PaymentOperation> existingOpt =
                operationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOpt.isPresent()) {
            PaymentOperation existing = existingOpt.get();
            validateIdempotencyParameters(
                    existing,
                    request.idempotencyKey(),
                    "REFUND",
                    request.orderId(),
                    request.customerId(),
                    normalizedAmount,
                    request.currency());
            return mapToPaymentResult(existing);
        }

        Customer customer =
                customerRepository
                        .findWithLockById(request.customerId())
                        .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));

        // A concurrent refund retry may have committed while waiting for lock
        existingOpt = operationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOpt.isPresent()) {
            PaymentOperation existing = existingOpt.get();
            validateIdempotencyParameters(
                    existing,
                    request.idempotencyKey(),
                    "REFUND",
                    request.orderId(),
                    request.customerId(),
                    normalizedAmount,
                    request.currency());
            return mapToPaymentResult(existing);
        }

        // The customer lock serializes refunds against the captured amount for this account.
        var operations =
                operationRepository.findByOrderId(request.orderId()).stream()
                        .filter(op -> Objects.equals(op.getCustomerId(), request.customerId()))
                        .toList();
        var captures =
                operations.stream()
                        .filter(op -> op.getStatus() == PaymentStatus.CONFIRMED)
                        .toList();
        if (captures.isEmpty()) {
            throw new InvalidPaymentTransitionException(
                    "Refund requires a confirmed payment for the same order and customer");
        }
        for (PaymentOperation capture : captures) {
            if (!request.currency().equalsIgnoreCase(capture.getCurrency())) {
                throw new CurrencyMismatchException(capture.getCurrency());
            }
        }
        BigDecimal capturedAmount =
                captures.stream()
                        .map(PaymentOperation::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedAmount =
                operations.stream()
                        .filter(op -> op.getStatus() == PaymentStatus.REFUNDED)
                        .map(PaymentOperation::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refundedAmount.add(normalizedAmount).compareTo(capturedAmount) > 0) {
            throw new InvalidPaymentTransitionException(
                    "Total refunds cannot exceed the captured amount");
        }

        customer.setAmountAvailable(customer.getAmountAvailable().add(normalizedAmount));
        customerRepository.save(customer);

        return recordAndPublishOperation(
                request.idempotencyKey(),
                request.orderId(),
                customer.getId(),
                "REFUND",
                normalizedAmount,
                SUPPORTED_CURRENCY,
                PaymentStatus.REFUNDED,
                "Payment refunded: "
                        + (request.reason() != null ? request.reason() : "Customer refund"));
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResult> getPaymentStatus(String idempotencyKey) {
        return operationRepository
                .findByIdempotencyKey(idempotencyKey)
                .map(this::mapToPaymentResult);
    }

    private PaymentResult recordAndPublishOperation(
            String idempotencyKey,
            Long orderId,
            Long customerId,
            String operationType,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String message) {
        Instant now = Instant.now();
        PaymentOperation op =
                new PaymentOperation(
                        null,
                        idempotencyKey,
                        orderId,
                        customerId,
                        operationType,
                        amount,
                        currency,
                        status,
                        message,
                        now,
                        now);

        PaymentOperation saved;
        try {
            saved = operationRepository.save(op);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race fallback
            return operationRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .map(this::mapToPaymentResult)
                    .orElseThrow(() -> ex);
        }

        PaymentResult result = mapToPaymentResult(saved);
        eventPublisher.publishEvent(new PaymentProcessedEvent(result));
        LOGGER.info(
                "Recorded and published PaymentProcessedEvent for key: {}, status: {}",
                idempotencyKey,
                status);
        return result;
    }

    private PaymentResult mapToPaymentResult(PaymentOperation op) {
        return new PaymentResult(
                op.getIdempotencyKey(),
                op.getOrderId(),
                op.getCustomerId(),
                op.getAmount(),
                op.getCurrency(),
                op.getStatus(),
                op.getMessage());
    }

    private void validateIdempotencyParameters(
            PaymentOperation existing,
            String idempotencyKey,
            String operationType,
            Long orderId,
            Long customerId,
            BigDecimal amount,
            String currency) {
        if (!Objects.equals(existing.getOperationType(), operationType)
                || !Objects.equals(existing.getOrderId(), orderId)
                || !Objects.equals(existing.getCustomerId(), customerId)
                || existing.getAmount().compareTo(amount) != 0
                || !currency.equalsIgnoreCase(existing.getCurrency())) {
            LOGGER.error("Idempotency conflict for payment key: {}", idempotencyKey);
            throw PaymentIdempotencyConflictException.forKey(idempotencyKey);
        }
    }

    private void validateCurrency(String currency) {
        if (currency == null
                || currency.isBlank()
                || !currency.equalsIgnoreCase(SUPPORTED_CURRENCY)) {
            throw new CurrencyMismatchException(currency);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be strictly positive");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
