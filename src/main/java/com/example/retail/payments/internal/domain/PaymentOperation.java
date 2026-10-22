/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.domain;

import com.example.retail.shared.payments.PaymentStatus;
import com.example.retail.shared.persistence.TsidSupplier;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_operations", schema = "payment")
public class PaymentOperation {

    @Id
    @Tsid(TsidSupplier.class)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "operation_type", nullable = false)
    private String operationType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "message")
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Short version = 0;

    public PaymentOperation() {}

    /** Creates an operation without an ID, using the supplied instant for both timestamps. */
    public PaymentOperation(
            String idempotencyKey,
            Long orderId,
            Long customerId,
            String operationType,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String message,
            Instant createdAt) {
        this(
                null,
                idempotencyKey,
                orderId,
                customerId,
                operationType,
                amount,
                currency,
                status,
                message,
                createdAt,
                createdAt);
    }

    /**
     * Creates an operation, defaulting a null currency to USD without validating or rounding
     * amounts.
     */
    public PaymentOperation(
            Long id,
            String idempotencyKey,
            Long orderId,
            Long customerId,
            String operationType,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String message,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.customerId = customerId;
        this.operationType = operationType;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Short getVersion() {
        return version;
    }

    public void setVersion(Short version) {
        this.version = version;
    }
}
