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
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import org.hibernate.proxy.HibernateProxy;

@Entity
@Table(
        name = "order_items",
        schema = "orders",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uc_order_items_product_order",
                        columnNames = {"product_code", "order_id"}))
public class OrderItem implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @Id
    @Tsid(TsidSupplier.class)
    private Long id;

    @Column(nullable = false, name = "product_code")
    private String productCode;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "product_price", nullable = false)
    private BigDecimal productPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    public Long getId() {
        return id;
    }

    public OrderItem setId(Long id) {
        this.id = id;
        return this;
    }

    public String getProductCode() {
        return productCode;
    }

    public OrderItem setProductCode(String productCode) {
        this.productCode = productCode;
        return this;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderItem setQuantity(int quantity) {
        this.quantity = quantity;
        return this;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public OrderItem setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
        return this;
    }

    public Order getOrder() {
        return order;
    }

    public OrderItem setOrder(Order order) {
        this.order = order;
        return this;
    }

    /**
     * Compares persistent classes and nonnull IDs, accounting for Hibernate proxies; identity is
     * equal.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> objectEffectiveClass =
                o instanceof HibernateProxy proxy
                        ? proxy.getHibernateLazyInitializer().getPersistentClass()
                        : o.getClass();
        Class<?> thisEffectiveClass =
                this instanceof HibernateProxy proxy
                        ? proxy.getHibernateLazyInitializer().getPersistentClass()
                        : this.getClass();
        if (thisEffectiveClass != objectEffectiveClass) {
            return false;
        }
        OrderItem orderItem = (OrderItem) o;
        return getId() != null && Objects.equals(getId(), orderItem.getId());
    }

    /** Returns the persistent class hash, independent of the assigned ID and proxy class. */
    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
