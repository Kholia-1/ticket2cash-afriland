package com.afriland.ticket2cash.loyalty;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A reusable cashback rule that Afriland admins configure once, then apply
 * to any uploaded transaction file. Rules can be edited but never deleted
 * once a batch has referenced them (audit trail requirement).
 */
@Entity
@Table(name = "loyalty_rules")
public class LoyaltyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoyaltyRuleType type;

    /**
     * Percentage rate (0..100). Used by FLAT_PERCENTAGE and CATEGORY_BASED.
     * For TIERED_VOLUME this is ignored — see {@link #tiersJson}.
     */
    @Column(precision = 6, scale = 3)
    private BigDecimal percentage;

    /**
     * JSON array of tiers for TIERED_VOLUME. Example:
     * [{"minVolume":0,"percentage":1.0},
     *  {"minVolume":1000000,"percentage":1.5},
     *  {"minVolume":10000000,"percentage":2.5}]
     */
    @Column(length = 2000)
    private String tiersJson;

    /** Only qualifying transaction rows with amount ≥ this threshold count. */
    @Column(precision = 18, scale = 2)
    private BigDecimal minTransactionAmount;

    /** Cap the per-client cashback for one batch. Null = uncapped. */
    @Column(precision = 15, scale = 2)
    private BigDecimal maxCashbackPerClient;

    /** For CATEGORY_BASED — transaction category that must match. */
    @Column(length = 50)
    private String categoryFilter;

    /**
     * Optional client tier restriction. Comma-separated list, e.g. "GOLD,PLATINUM".
     * Blank = applies to all tiers.
     */
    @Column(length = 200)
    private String tierFilter;

    /** Only credit clients whose total qualifying volume in period ≥ this. */
    @Column(precision = 18, scale = 2)
    private BigDecimal minPeriodVolume;

    // ------------------------------------------------------------------
    // Banking-informed criteria (new in V4)
    // ------------------------------------------------------------------

    /**
     * Segment filter: INDIVIDUAL, COMPANY, or null (ALL).
     * Used by the "who is this rule for?" dropdown on the admin UI.
     * If set, only clients whose entityType matches this value are eligible.
     */
    @Column(length = 15)
    private String entityTypeFilter;

    /**
     * Minimum number of qualifying transactions in the period.
     * Rewards active users, not just big single-transaction spenders.
     * Null / 0 = no minimum.
     */
    private Integer minTransactionCount;

    /**
     * Minimum average transaction value in the period.
     * Filter out clients doing lots of tiny transactions who wouldn't
     * qualify as "high-quality" activity.
     * Null / 0 = no minimum.
     */
    @Column(precision = 18, scale = 2)
    private BigDecimal minAvgTransactionValue;

    /**
     * Minimum client tenure (months since account opened).
     * Rewards loyalty over time — new customers don't get big cashback rewards.
     * Requires {@link LoyaltyClient#getAccountOpenedDate()} to be set.
     * Null / 0 = no minimum.
     */
    private Integer minAccountAgeMonths;

    // ------------------------------------------------------------------

    private Boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    public LoyaltyRule() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (active == null) active = true;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LoyaltyRuleType getType() { return type; }
    public void setType(LoyaltyRuleType type) { this.type = type; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
    public String getTiersJson() { return tiersJson; }
    public void setTiersJson(String tiersJson) { this.tiersJson = tiersJson; }
    public BigDecimal getMinTransactionAmount() { return minTransactionAmount; }
    public void setMinTransactionAmount(BigDecimal v) { this.minTransactionAmount = v; }
    public BigDecimal getMaxCashbackPerClient() { return maxCashbackPerClient; }
    public void setMaxCashbackPerClient(BigDecimal v) { this.maxCashbackPerClient = v; }
    public String getCategoryFilter() { return categoryFilter; }
    public void setCategoryFilter(String categoryFilter) { this.categoryFilter = categoryFilter; }
    public String getTierFilter() { return tierFilter; }
    public void setTierFilter(String tierFilter) { this.tierFilter = tierFilter; }
    public BigDecimal getMinPeriodVolume() { return minPeriodVolume; }
    public void setMinPeriodVolume(BigDecimal v) { this.minPeriodVolume = v; }
    public String getEntityTypeFilter() { return entityTypeFilter; }
    public void setEntityTypeFilter(String entityTypeFilter) { this.entityTypeFilter = entityTypeFilter; }
    public Integer getMinTransactionCount() { return minTransactionCount; }
    public void setMinTransactionCount(Integer minTransactionCount) { this.minTransactionCount = minTransactionCount; }
    public BigDecimal getMinAvgTransactionValue() { return minAvgTransactionValue; }
    public void setMinAvgTransactionValue(BigDecimal v) { this.minAvgTransactionValue = v; }
    public Integer getMinAccountAgeMonths() { return minAccountAgeMonths; }
    public void setMinAccountAgeMonths(Integer minAccountAgeMonths) { this.minAccountAgeMonths = minAccountAgeMonths; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
