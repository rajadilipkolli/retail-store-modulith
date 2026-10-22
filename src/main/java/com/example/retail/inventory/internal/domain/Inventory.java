/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.domain;

import com.example.retail.shared.persistence.TsidSupplier;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory", schema = "inventory")
public class Inventory {

    @Id
    @Tsid(TsidSupplier.class)
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true, updatable = false)
    private String productCode;

    @Column(name = "quantity", nullable = false)
    private Integer availableQuantity = 0;

    @Column(name = "reserved_items", nullable = false)
    private Integer reservedItems = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Short version = 0;

    public Inventory() {}

    public Inventory(
            Long id, String productCode, Integer availableQuantity, Integer reservedItems) {
        this.id = id;
        this.productCode = productCode;
        this.availableQuantity = availableQuantity != null ? availableQuantity : 0;
        this.reservedItems = reservedItems != null ? reservedItems : 0;
    }

    public Long getId() {
        return id;
    }

    public Inventory setId(Long id) {
        this.id = id;
        return this;
    }

    public String getProductCode() {
        return productCode;
    }

    public Inventory setProductCode(String productCode) {
        this.productCode = productCode;
        return this;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public Inventory setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
        return this;
    }

    public Integer getReservedItems() {
        return reservedItems;
    }

    public Inventory setReservedItems(Integer reservedItems) {
        this.reservedItems = reservedItems;
        return this;
    }

    public Short getVersion() {
        return version;
    }

    public Inventory setVersion(Short version) {
        this.version = version;
        return this;
    }
}
