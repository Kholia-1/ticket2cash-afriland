package com.afriland.ticket2cash.clo;

import com.afriland.ticket2cash.merchant.Merchant;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A Card-Linked Offer (CLO) — GL-04 in the strategic cahier.
 *
 * <p>An offer is <b>fully merchant-funded</b>: the sponsoring merchant pays for
 * the cashback/points reward. Afriland acts as the distribution channel via
 * its prepaid card portfolio.
 *
 * <p>Targeting is done at three levels:
 * <ol>
 *   <li><b>BIN</b> — comma-separated list of prepaid card BINs eligible</li>
 *   <li><b>Opt-in</b> — the cardholder must have opted in via CloOptIn</li>
 *   <li><b>Merchant</b> — only transactions at the sponsoring merchant count</li>
 * </ol>
 *
 * <p>Budget and per-user caps prevent runaway spend by the merchant.
 */
@Entity
@Table(name = "clo_offers", indexes = {
        @Index(name = "idx_clo_status", columnList = "status"),
        @Index(name = "idx_clo_merchant", columnList = "merchant_id")
})
public class CloOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short marketing name shown to the cardholder. */
    @Column(nullable = false, length = 200)
    private String name;

    /** Long description shown in the offer detail. */
    @Column(length = 2000)
    private String description;

    /** Sponsoring merchant (who pays for the reward). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    /** Reward mechanics. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CloRewardType rewardType;

    /**
     * Reward value. Semantics depend on rewardType:
     * <ul>
     *   <li>CASHBACK_PERCENT — percentage (e.g. 5.0 = 5%)</li>
     *   <li>CASHBACK_FIXED — FCFA amount</li>
     *   <li>POINTS_BONUS — number of points</li>
     * </ul>
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal rewardValue;

    /**
     * Comma-separated list of eligible BINs (6 digits each), e.g.
     * "456120,456121". Empty/null = all Afriland prepaid BINs.
     */
    @Column(length = 500)
    private String targetBins;

    /** Minimum transaction amount to qualify. */
    @Column(precision = 15, scale = 2)
    private BigDecimal minTransactionAmount;

    /** Maximum reward per cardholder over the offer's lifetime. */
    @Column(precision = 15, scale = 2)
    private BigDecimal maxRewardPerCardholder;

    /** Total budget for the offer. When exhausted, offer auto-pauses. */
    @Column(precision = 15, scale = 2)
    private BigDecimal totalBudget;

    /** How much of the budget has been used so far. */
    @Column(precision = 15, scale = 2)
    private BigDecimal budgetUsed;

    /** Count of redemptions so far. */
    private Long redemptionCount;

    /** Optional canal filter (E-commerce, F2F, etc.), as string to avoid coupling. */
    @Column(length = 30)
    private String channelFilter;

    private LocalDate validFrom;
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CloOfferStatus status;

    /** Auditing. */
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CloOffer() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = CloOfferStatus.DRAFT;
        if (budgetUsed == null) budgetUsed = BigDecimal.ZERO;
        if (redemptionCount == null) redemptionCount = 0L;
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
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public CloRewardType getRewardType() { return rewardType; }
    public void setRewardType(CloRewardType rewardType) { this.rewardType = rewardType; }
    public BigDecimal getRewardValue() { return rewardValue; }
    public void setRewardValue(BigDecimal rewardValue) { this.rewardValue = rewardValue; }
    public String getTargetBins() { return targetBins; }
    public void setTargetBins(String targetBins) { this.targetBins = targetBins; }
    public BigDecimal getMinTransactionAmount() { return minTransactionAmount; }
    public void setMinTransactionAmount(BigDecimal v) { this.minTransactionAmount = v; }
    public BigDecimal getMaxRewardPerCardholder() { return maxRewardPerCardholder; }
    public void setMaxRewardPerCardholder(BigDecimal v) { this.maxRewardPerCardholder = v; }
    public BigDecimal getTotalBudget() { return totalBudget; }
    public void setTotalBudget(BigDecimal totalBudget) { this.totalBudget = totalBudget; }
    public BigDecimal getBudgetUsed() { return budgetUsed; }
    public void setBudgetUsed(BigDecimal budgetUsed) { this.budgetUsed = budgetUsed; }
    public Long getRedemptionCount() { return redemptionCount; }
    public void setRedemptionCount(Long redemptionCount) { this.redemptionCount = redemptionCount; }
    public String getChannelFilter() { return channelFilter; }
    public void setChannelFilter(String channelFilter) { this.channelFilter = channelFilter; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public CloOfferStatus getStatus() { return status; }
    public void setStatus(CloOfferStatus status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
