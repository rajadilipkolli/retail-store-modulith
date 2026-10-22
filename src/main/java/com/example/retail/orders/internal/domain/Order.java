/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.domain;

import com.example.retail.shared.orders.Address;
import com.example.retail.shared.persistence.TsidSupplier;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", schema = "orders")
public class Order implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @Id
    @Tsid(TsidSupplier.class)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    private String source;

    @Embedded
    @AttributeOverrides(
            value = {
                @AttributeOverride(
                        name = "addressLine1",
                        column = @Column(name = "delivery_address_line1", nullable = false)),
                @AttributeOverride(
                        name = "addressLine2",
                        column = @Column(name = "delivery_address_line2")),
                @AttributeOverride(
                        name = "city",
                        column = @Column(name = "delivery_address_city", nullable = false)),
                @AttributeOverride(
                        name = "state",
                        column = @Column(name = "delivery_address_state", nullable = false)),
                @AttributeOverride(
                        name = "zipCode",
                        column = @Column(name = "delivery_address_zip_code", nullable = false)),
                @AttributeOverride(
                        name = "country",
                        column = @Column(name = "delivery_address_country", nullable = false)),
            })
    private Address deliveryAddress;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Version private Short version;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public Order setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Order setCustomerId(Long customerId) {
        this.customerId = customerId;
        return this;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Order setStatus(OrderStatus status) {
        this.status = status;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Order setSource(String source) {
        this.source = source;
        return this;
    }

    public Address getDeliveryAddress() {
        return deliveryAddress;
    }

    public Order setDeliveryAddress(Address deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
        return this;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public Order setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
        return this;
    }

    public Short getVersion() {
        return version;
    }

    public Order setVersion(Short version) {
        this.version = version;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Order setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public Order setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Order setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
        return this;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public Order setLastModifiedDate(LocalDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
        return this;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Order setItems(List<OrderItem> items) {
        this.items = items;
        return this;
    }

    /** Adds the item and sets its owning order to this order. */
    public void addOrderItem(OrderItem orderItem) {
        items.add(orderItem);
        orderItem.setOrder(this);
    }

    /**
     * Removes the item and clears its owning order, even if it was not in this order’s item list.
     */
    public void removeOrderItem(OrderItem orderItem) {
        items.remove(orderItem);
        orderItem.setOrder(null);
    }
}
