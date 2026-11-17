package com.core.inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String sku;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false)
    private Integer quantity;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;
    @Version
    @JsonIgnore
    private Long version;

    public InventoryItem() {
    }

    public InventoryItem(Long id, String sku, String name, String description, Integer quantity,
                         BigDecimal price, Long version) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
        this.price = price;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
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
        if (!(other instanceof InventoryItem item)) return false;
        return item.canEqual(this)
                && Objects.equals(getId(), item.getId())
                && Objects.equals(getSku(), item.getSku())
                && Objects.equals(getName(), item.getName())
                && Objects.equals(getDescription(), item.getDescription())
                && Objects.equals(getQuantity(), item.getQuantity())
                && Objects.equals(getPrice(), item.getPrice())
                && Objects.equals(getVersion(), item.getVersion());
    }

    protected boolean canEqual(Object other) {
        return other instanceof InventoryItem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getSku(), getName(), getDescription(), getQuantity(), getPrice(), getVersion());
    }

    @Override
    public String toString() {
        return "InventoryItem(id=" + getId() + ", sku=" + getSku() + ", name=" + getName()
                + ", description=" + getDescription() + ", quantity=" + getQuantity()
                + ", price=" + getPrice() + ", version=" + getVersion() + ")";
    }
}
