package com.afriland.ticket2cash.points;

import com.afriland.ticket2cash.campaign.MccCode;
import com.afriland.ticket2cash.merchant.Merchant;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A rule that defines how points are earned from a matching loyalty transaction.
 *
 * <p>Matching precedence (highest first): SKU → MCC → merchant → global default.
 * Only the highest-priority matching rule applies to a given transaction.
 *
 * <p>Rate is expressed as "points per 1000 FCFA spent" for clarity — e.g.
 * {@code pointsPer1000Fcfa = 10} on a 15 000 FCFA transaction earns 150 points.
 */
@Entity
@Table(name = "points_rules", indexes = {
        @Index(name = "idx_pr_active", columnList = "active"),
        @Index(name = "idx_pr_priority", columnList = "priority")
})
public class PointsRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    /**
     * Points earned per 1000 FCFA of transaction amount. E.g. 10 = 1% return
     * in points equivalent.
     */
    @Column(nullable = false)
    private Long pointsPer1000Fcfa;

    /**
     * Optional multiplier applied on top of the base rate. Default 1.0.
     * E.g. 2.0 doubles the earn rate for premium tiers or launch campaigns.
     */
    @Column(precision = 6, scale = 3)
    private BigDecimal multiplier;

    /** Number of months earned points remain valid. Default 12. */
    private Integer validityMonths;

    /** Minimum transaction amount to qualify. Null = no minimum. */
    @Column(precision = 18, scale = 2)
    private BigDecimal minSpendAmount;

    // -------------------------------------------------------------- Filters

    /** Only match transactions at this merchant. Null = any merchant. */
    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    /** Only match transactions in this MCC. Null = any MCC. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MccCode mccCode;

    /**
     * Only match transactions of this product SKU (comma-separated for
     * multiple). Null = any product.
     */
    @Column(length = 500)
    private String productSku;

    /** Priority for matching. Higher = tried first. Default 0. */
    @Column(nullable = false)
    private Integer priority;

    /** Whether the rule is currently in effect. */
    @Column(nullable = false)
    private Boolean active;

    /** Optional validity window on the rule itself. */
    private LocalDate startDate;
    private LocalDate endDate;

    /** For auditing. */
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PointsRule() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (active == null) active = true;
        if (validityMonths == null) validityMonths = 12;
        if (priority == null) priority = 0;
        if (multiplier == null) multiplier = BigDecimal.ONE;
        if (pointsPer1000Fcfa == null) pointsPer1000Fcfa = 0L;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getPointsPer1000Fcfa() { return pointsPer1000Fcfa; }
    public void setPointsPer1000Fcfa(Long v) { this.pointsPer1000Fcfa = v; }
    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }
    public Integer getValidityMonths() { return validityMonths; }
    public void setValidityMonths(Integer v) { this.validityMonths = v; }
    public BigDecimal getMinSpendAmount() { return minSpendAmount; }
    public void setMinSpendAmount(BigDecimal v) { this.minSpendAmount = v; }
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public MccCode getMccCode() { return mccCode; }
    public void setMccCode(MccCode mccCode) { this.mccCode = mccCode; }
    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
