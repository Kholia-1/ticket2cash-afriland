package com.afriland.ticket2cash.loyalty;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A configurable loyalty tier — GL-07 in the strategic cahier.
 *
 * <p>Tiers are ordered from lowest ({@code sortOrder = 0}) to highest. A client
 * progresses to the next tier when their cumulative spend over the evaluation
 * window ({@link #evaluationMonths}) reaches {@link #minCumulativeSpend} AND
 * transaction count reaches {@link #minTransactionCount}.
 *
 * <p>If they no longer meet those thresholds over a subsequent window, they
 * are downgraded after {@link #graceMonths} months of grace period.
 *
 * <p>Each tier can define benefits:
 * <ul>
 *   <li>{@code cashbackBonusPercent} — added on top of any base campaign cashback</li>
 *   <li>{@code benefitsSummary} — free-text description shown to customer</li>
 * </ul>
 */
@Entity
@Table(name = "loyalty_tiers", indexes = {
        @Index(name = "idx_tier_sort", columnList = "sortOrder")
})
public class LoyaltyTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name — e.g. "Essentiel", "Premium", "Prestige", "Elite". */
    @Column(nullable = false, length = 50)
    private String name;

    /** Short description shown in the client card. */
    @Column(length = 500)
    private String description;

    /** Lower = base tier, higher = elite tier. Determines progression order. */
    private Integer sortOrder;

    /** Minimum cumulative spend over the evaluation window to reach this tier. */
    @Column(precision = 18, scale = 2)
    private BigDecimal minCumulativeSpend;

    /** Minimum number of qualifying transactions over the evaluation window. */
    private Integer minTransactionCount;

    /** Evaluation window in months (typically 12). */
    private Integer evaluationMonths;

    /** Grace period in months before downgrade. Typically 6. */
    private Integer graceMonths;

    /** Bonus cashback % added on top of base cashback (e.g. 0.5 = +0.5%). */
    @Column(precision = 6, scale = 3)
    private BigDecimal cashbackBonusPercent;

    /** Free-text list of benefits: cashback bonus, access to exclusive offers, etc. */
    @Column(length = 1000)
    private String benefitsSummary;

    /** Optional colour hex code for UI badge (e.g. "#c1121f"). */
    @Column(length = 10)
    private String colorHex;

    /** Optional icon/emoji for UI display (e.g. "🥇"). */
    @Column(length = 10)
    private String icon;

    private Boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public LoyaltyTier() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (active == null) active = true;
        if (sortOrder == null) sortOrder = 0;
        if (evaluationMonths == null) evaluationMonths = 12;
        if (graceMonths == null) graceMonths = 6;
        if (minCumulativeSpend == null) minCumulativeSpend = BigDecimal.ZERO;
        if (minTransactionCount == null) minTransactionCount = 0;
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
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public BigDecimal getMinCumulativeSpend() { return minCumulativeSpend; }
    public void setMinCumulativeSpend(BigDecimal v) { this.minCumulativeSpend = v; }
    public Integer getMinTransactionCount() { return minTransactionCount; }
    public void setMinTransactionCount(Integer v) { this.minTransactionCount = v; }
    public Integer getEvaluationMonths() { return evaluationMonths; }
    public void setEvaluationMonths(Integer v) { this.evaluationMonths = v; }
    public Integer getGraceMonths() { return graceMonths; }
    public void setGraceMonths(Integer v) { this.graceMonths = v; }
    public BigDecimal getCashbackBonusPercent() { return cashbackBonusPercent; }
    public void setCashbackBonusPercent(BigDecimal v) { this.cashbackBonusPercent = v; }
    public String getBenefitsSummary() { return benefitsSummary; }
    public void setBenefitsSummary(String v) { this.benefitsSummary = v; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
