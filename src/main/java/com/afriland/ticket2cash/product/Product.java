package com.afriland.ticket2cash.product;

import com.afriland.ticket2cash.merchant.Merchant;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private String name;
    private String ticketDesignation;
    private String synonyms;
    private String category;
    private String brand;

    private BigDecimal price;

    private String groupKey;

    @Enumerated(EnumType.STRING)
    private CashbackType cashbackType;

    private BigDecimal cashbackValue;

    private Boolean active;

    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    public Product() {
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }

        if (this.active == null) {
            this.active = true;
        }

        if (this.cashbackType == null) {
            this.cashbackType = CashbackType.NONE;
        }
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getTicketDesignation() {
        return ticketDesignation;
    }

    public String getSynonyms() {
        return synonyms;
    }

    public String getCategory() {
        return category;
    }

    public String getBrand() {
        return brand;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public CashbackType getCashbackType() {
        return cashbackType;
    }

    public BigDecimal getCashbackValue() {
        return cashbackValue;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTicketDesignation(String ticketDesignation) {
        this.ticketDesignation = ticketDesignation;
    }

    public void setSynonyms(String synonyms) {
        this.synonyms = synonyms;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public void setCashbackType(CashbackType cashbackType) {
        this.cashbackType = cashbackType;
    }

    public void setCashbackValue(BigDecimal cashbackValue) {
        this.cashbackValue = cashbackValue;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }
}