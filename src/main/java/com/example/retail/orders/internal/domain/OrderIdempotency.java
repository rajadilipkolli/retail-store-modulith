/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.domain;

import com.example.retail.shared.persistence.TsidSupplier;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "order_idempotency",
        schema = "orders",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uc_order_idempotency_customer_key",
                        columnNames = {"customer_id", "idempotency_key"}))
public class OrderIdempotency implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Tsid(TsidSupplier.class)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    public Long getId() {
        return id;
    }

    public OrderIdempotency setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public OrderIdempotency setCustomerId(Long customerId) {
        this.customerId = customerId;
        return this;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OrderIdempotency setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public OrderIdempotency setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
        return this;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderIdempotency setOrderId(Long orderId) {
        this.orderId = orderId;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public OrderIdempotency setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public OrderIdempotency setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
        return this;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public OrderIdempotency setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public OrderIdempotency setLastModifiedDate(LocalDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
        return this;
    }
}
