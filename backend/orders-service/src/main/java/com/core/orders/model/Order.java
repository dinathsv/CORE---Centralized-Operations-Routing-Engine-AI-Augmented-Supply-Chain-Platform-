package com.core.orders.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String customerName;
    @Column(nullable = false, length = 100)
    private String sku;
    @Column(nullable = false)
    private Integer quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    @JsonIgnore
    private Long version;

    public Order() {
    }

    public Order(Long id, String customerName, String sku, Integer quantity, OrderStatus status,
                 Instant createdAt, Long version) {
        this.id = id;
        this.customerName = customerName;
        this.sku = sku;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Order order)) return false;
        return order.canEqual(this)
                && Objects.equals(getId(), order.getId())
                && Objects.equals(getCustomerName(), order.getCustomerName())
                && Objects.equals(getSku(), order.getSku())
                && Objects.equals(getQuantity(), order.getQuantity())
                && getStatus() == order.getStatus()
                && Objects.equals(getCreatedAt(), order.getCreatedAt())
                && Objects.equals(getVersion(), order.getVersion());
    }

    protected boolean canEqual(Object other) {
        return other instanceof Order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getCustomerName(), getSku(), getQuantity(), getStatus(),
                getCreatedAt(), getVersion());
    }

    @Override
    public String toString() {
        return "Order(id=" + getId() + ", customerName=" + getCustomerName() + ", sku=" + getSku()
                + ", quantity=" + getQuantity() + ", status=" + getStatus() + ", createdAt="
                + getCreatedAt() + ", version=" + getVersion() + ")";
    }

    public enum OrderStatus {
        PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }
}
