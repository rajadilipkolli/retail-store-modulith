/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.domain;

import com.example.retail.shared.inventory.InventoryReservationStatus;
import com.example.retail.shared.persistence.TsidSupplier;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "inventory_reservations", schema = "inventory")
public class InventoryReservation {

    @Id
    @Tsid(TsidSupplier.class)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InventoryReservationStatus status;

    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Short version = 0;

    public InventoryReservation() {}

    public InventoryReservation(
            Long id,
            String idempotencyKey,
            Long orderId,
            InventoryReservationStatus status,
            String itemsJson,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.status = status;
        this.itemsJson = itemsJson;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public InventoryReservation setId(Long id) {
        this.id = id;
        return this;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public InventoryReservation setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public Long getOrderId() {
        return orderId;
    }

    public InventoryReservation setOrderId(Long orderId) {
        this.orderId = orderId;
        return this;
    }

    public InventoryReservationStatus getStatus() {
        return status;
    }

    public InventoryReservation setStatus(InventoryReservationStatus status) {
        this.status = status;
        return this;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public InventoryReservation setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public InventoryReservation setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public InventoryReservation setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Short getVersion() {
        return version;
    }

    public InventoryReservation setVersion(Short version) {
        this.version = version;
        return this;
    }
}
