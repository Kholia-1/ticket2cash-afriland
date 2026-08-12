package com.afriland.ticket2cash.product;

import java.math.BigDecimal;

public class ProductRequest {

    private Long merchantId;
    private String sku;
    private String name;
    private String ticketDesignation;
    private String synonyms;
    private String category;
    private String brand;
    private BigDecimal price;
    private String groupKey;
    private CashbackType cashbackType;
    private BigDecimal cashbackValue;
    private Boolean active;

    public Long getMerchantId() { return merchantId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getTicketDesignation() { return ticketDesignation; }
    public String getSynonyms() { return synonyms; }
    public String getCategory() { return category; }
    public String getBrand() { return brand; }
    public BigDecimal getPrice() { return price; }
    public String getGroupKey() { return groupKey; }
    public CashbackType getCashbackType() { return cashbackType; }
    public BigDecimal getCashbackValue() { return cashbackValue; }
    public Boolean getActive() { return active; }

    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public void setSku(String sku) { this.sku = sku; }
    public void setName(String name) { this.name = name; }
    public void setTicketDesignation(String ticketDesignation) { this.ticketDesignation = ticketDesignation; }
    public void setSynonyms(String synonyms) { this.synonyms = synonyms; }
    public void setCategory(String category) { this.category = category; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
    public void setCashbackType(CashbackType cashbackType) { this.cashbackType = cashbackType; }
    public void setCashbackValue(BigDecimal cashbackValue) { this.cashbackValue = cashbackValue; }
    public void setActive(Boolean active) { this.active = active; }
}