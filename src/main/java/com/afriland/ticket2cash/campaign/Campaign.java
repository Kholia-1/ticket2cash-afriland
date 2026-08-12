package com.afriland.ticket2cash.campaign;

import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.product.CashbackType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A cashback campaign. The core entity of the "moteur d'animation de campagnes".
 *
 * <p>A campaign has a {@link CampaignTriggerType} that determines <b>what event
 * triggers cashback computation</b>. The four possible triggers correspond to
 * four data sources:
 * <ul>
 *   <li>{@code MERCHANT_TRANSACTION} — bank transaction upload / POS stream</li>
 *   <li>{@code PRODUCT_PURCHASE}    — OCR ticket + product matching</li>
 *   <li>{@code VOLUME_THRESHOLD}    — cumulative bank spend at a merchant</li>
 *   <li>{@code POS_WEBHOOK_EVENT}   — real-time POS webhook</li>
 * </ul>
 *
 * <p>Each trigger has its own configuration fields (e.g. {@code targetProductSkus}
 * for {@code PRODUCT_PURCHASE}, {@code volumeThreshold} for {@code VOLUME_THRESHOLD}).
 * Fields that aren't relevant to a given trigger are simply left null.
 *
 * <p>Existing pre-Phase-1 campaigns default to {@code MERCHANT_TRANSACTION} and
 * keep working exactly as before — Hibernate {@code ddl-auto=update} adds the
 * new columns as nullable, no data is lost.
 */
@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -------------------------------------------------------------- identity

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    // -------------------------------------------------------------- ownership

    /** Who owns this campaign. See {@link CampaignOwnerType}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private CampaignOwnerType ownerType;

    /**
     * The merchant this campaign targets:
     *   - always set for MERCHANT-owned campaigns (forced to the partner's own merchant)
     *   - may be null for ADMIN-owned "global" campaigns (rare — most bank campaigns
     *     still target a specific merchant like Netflix or Total).
     */
    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    // -------------------------------------------------------------- trigger (THE KEY FIELD)

    /** Which type of event triggers this campaign's cashback computation. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CampaignTriggerType triggerType;

    // -------------------------------------------------------------- window

    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status;

    // -------------------------------------------------------------- common filters

    /**
     * Target segment: INDIVIDUAL / COMPANY / null (all).
     * Applies mainly to MERCHANT_TRANSACTION and VOLUME_THRESHOLD.
     */
    @Column(length = 15)
    private String entityTypeFilter;

    /**
     * Optional client-tier restriction, comma-separated (e.g. "GOLD,PLATINUM").
     */
    @Column(length = 200)
    private String tierFilter;

    /** Only transactions with amount ≥ this count as qualifying. */
    @Column(precision = 18, scale = 2)
    private BigDecimal minTransactionAmount;

    /** Cap cashback per single client for this campaign. Null = uncapped. */
    @Column(precision = 15, scale = 2)
    private BigDecimal maxCashbackPerClient;

    // -------------------------------------------------------------- cashback config

    @Enumerated(EnumType.STRING)
    private CashbackType cashbackType;

    @Column(precision = 18, scale = 6)
    private BigDecimal cashbackValue;

    private BigDecimal dailyLimitPerClient;
    private BigDecimal monthlyLimitPerClient;
    private BigDecimal totalBudget;

    // -------------------------------------------------------------- trigger-specific fields

    /**
     * For {@code PRODUCT_PURCHASE}: comma-separated SKUs that unlock cashback.
     * Example: "SHAMP-01,SHAMP-02".
     */
    @Column(length = 2000)
    private String targetProductSkus;

    /**
     * For {@code VOLUME_THRESHOLD}: minimum cumulative spend by a client at the
     * merchant during the campaign window to earn cashback.
     */
    @Column(precision = 18, scale = 2)
    private BigDecimal volumeThreshold;

    /**
     * For {@code POS_WEBHOOK_EVENT}: minimum single-transaction amount at the
     * merchant POS to fire the cashback.
     */
    @Column(precision = 18, scale = 2)
    private BigDecimal amountThreshold;

    /**
     * For {@code MERCHANT_TRANSACTION}: optional bank-side transaction category
     * filter (e.g. "CARD_PURCHASE", "MOBILE_MONEY", "WIRE_TRANSFER"). Null = all.
     */
    @Column(length = 50)
    private String categoryFilter;

    // -------------------------------------------------------------- MCC / channel / corridor (GL-01)

    /**
     * Merchant Category Code filter (e.g. MCC_5411 = supermarkets, MCC_5541 = gas stations).
     * Only transactions matching this MCC qualify. Null = no MCC filter.
     * Relevant for {@code MERCHANT_TRANSACTION} campaigns.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MccCode mccCode;

    /**
     * Payment channel filter (E-commerce, in-store, mobile wallet, CNP, ATM).
     * Null or ALL = no channel filter.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentChannel channelFilter;

    /**
     * Geographic corridor filter (domestic / international / cross-border CEMEA).
     * Null or ALL = no corridor filter.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentCorridor corridorFilter;

    /**
     * Card BIN range start (first 6-8 digits of card number). Used to target
     * specific card products (e.g. only "Gold Business" cards in BIN 456120-456129).
     * Null = no BIN restriction.
     */
    @Column(length = 10)
    private String cardBinStart;

    /** Card BIN range end (inclusive). Null = no BIN restriction. */
    @Column(length = 10)
    private String cardBinEnd;

    // -------------------------------------------------------------- audit

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(length = 60)
    private String createdBy;

    // -------------------------------------------------------------- lifecycle

    public Campaign() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = CampaignStatus.DRAFT;
        if (triggerType == null) triggerType = CampaignTriggerType.MERCHANT_TRANSACTION;
        if (ownerType == null) ownerType = CampaignOwnerType.MERCHANT;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------------- getters/setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public CampaignOwnerType getOwnerType() { return ownerType; }
    public void setOwnerType(CampaignOwnerType ownerType) { this.ownerType = ownerType; }
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public CampaignTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(CampaignTriggerType triggerType) { this.triggerType = triggerType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus status) { this.status = status; }
    public String getEntityTypeFilter() { return entityTypeFilter; }
    public void setEntityTypeFilter(String entityTypeFilter) { this.entityTypeFilter = entityTypeFilter; }
    public String getTierFilter() { return tierFilter; }
    public void setTierFilter(String tierFilter) { this.tierFilter = tierFilter; }
    public BigDecimal getMinTransactionAmount() { return minTransactionAmount; }
    public void setMinTransactionAmount(BigDecimal v) { this.minTransactionAmount = v; }
    public BigDecimal getMaxCashbackPerClient() { return maxCashbackPerClient; }
    public void setMaxCashbackPerClient(BigDecimal v) { this.maxCashbackPerClient = v; }
    public CashbackType getCashbackType() { return cashbackType; }
    public void setCashbackType(CashbackType cashbackType) { this.cashbackType = cashbackType; }
    public BigDecimal getCashbackValue() { return cashbackValue; }
    public void setCashbackValue(BigDecimal cashbackValue) { this.cashbackValue = cashbackValue; }
    public BigDecimal getDailyLimitPerClient() { return dailyLimitPerClient; }
    public void setDailyLimitPerClient(BigDecimal v) { this.dailyLimitPerClient = v; }
    public BigDecimal getMonthlyLimitPerClient() { return monthlyLimitPerClient; }
    public void setMonthlyLimitPerClient(BigDecimal v) { this.monthlyLimitPerClient = v; }
    public BigDecimal getTotalBudget() { return totalBudget; }
    public void setTotalBudget(BigDecimal totalBudget) { this.totalBudget = totalBudget; }
    public String getTargetProductSkus() { return targetProductSkus; }
    public void setTargetProductSkus(String targetProductSkus) { this.targetProductSkus = targetProductSkus; }
    public BigDecimal getVolumeThreshold() { return volumeThreshold; }
    public void setVolumeThreshold(BigDecimal volumeThreshold) { this.volumeThreshold = volumeThreshold; }
    public BigDecimal getAmountThreshold() { return amountThreshold; }
    public void setAmountThreshold(BigDecimal amountThreshold) { this.amountThreshold = amountThreshold; }
    public String getCategoryFilter() { return categoryFilter; }
    public void setCategoryFilter(String categoryFilter) { this.categoryFilter = categoryFilter; }
    public MccCode getMccCode() { return mccCode; }
    public void setMccCode(MccCode mccCode) { this.mccCode = mccCode; }
    public PaymentChannel getChannelFilter() { return channelFilter; }
    public void setChannelFilter(PaymentChannel channelFilter) { this.channelFilter = channelFilter; }
    public PaymentCorridor getCorridorFilter() { return corridorFilter; }
    public void setCorridorFilter(PaymentCorridor corridorFilter) { this.corridorFilter = corridorFilter; }
    public String getCardBinStart() { return cardBinStart; }
    public void setCardBinStart(String cardBinStart) { this.cardBinStart = cardBinStart; }
    public String getCardBinEnd() { return cardBinEnd; }
    public void setCardBinEnd(String cardBinEnd) { this.cardBinEnd = cardBinEnd; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
