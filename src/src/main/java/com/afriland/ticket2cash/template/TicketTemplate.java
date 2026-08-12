package com.afriland.ticket2cash.template;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_templates")
public class TicketTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Long merchantId;
    private String storeNamePattern;
    private String totalKeyword;
    private String dateFormat;

    @Column(length = 2000)
    private String sampleText;

    private Boolean active;
    private LocalDateTime createdAt;

    public TicketTemplate() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (active == null) active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getStoreNamePattern() { return storeNamePattern; }
    public void setStoreNamePattern(String storeNamePattern) { this.storeNamePattern = storeNamePattern; }
    public String getTotalKeyword() { return totalKeyword; }
    public void setTotalKeyword(String totalKeyword) { this.totalKeyword = totalKeyword; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public String getSampleText() { return sampleText; }
    public void setSampleText(String sampleText) { this.sampleText = sampleText; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
