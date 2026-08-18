package com.afriland.ticket2cash.campaign;

import com.afriland.ticket2cash.product.CashbackType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating or updating a campaign. Mirrors the {@link Campaign}
 * entity but is intentionally lenient — Jackson tolerates missing fields, and
 * the controller validates and applies defaults before persisting.
 *
 * <p>Trigger-specific fields (e.g. {@code targetProductSkus},
 * {@code volumeThreshold}) are simply ignored when they don't apply to the
 * chosen {@link #triggerType}.
 */
public class CampaignRequest {

    // Identity
    private String name;
    private String description;

    // Ownership
    private CampaignOwnerType ownerType;
    private Long merchantId;

    // Trigger (KEY FIELD)
    private CampaignTriggerType triggerType;

    // Window
    private LocalDate startDate;
    private LocalDate endDate;
    private CampaignStatus status;

    // Common filters
    private String entityTypeFilter;
    private String tierFilter;
    private BigDecimal minTransactionAmount;
    private BigDecimal maxCashbackPerClient;

    // Cashback config
    private CashbackType cashbackType;
    private BigDecimal cashbackValue;
    private Boolean loyaltyBonusEnabled;
    private BigDecimal dailyLimitPerClient;
    private BigDecimal monthlyLimitPerClient;
    private BigDecimal totalBudget;

    // Trigger-specific
    private String targetProductSkus;
    private BigDecimal volumeThreshold;
    private BigDecimal amountThreshold;
    private String categoryFilter;

    // GL-01 filters (MCC / channel / corridor / BIN)
    private MccCode mccCode;
    private PaymentChannel channelFilter;
    private PaymentCorridor corridorFilter;
    private String cardBinStart;
    private String cardBinEnd;

    // Getters/Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public CampaignOwnerType getOwnerType() { return ownerType; }
    public void setOwnerType(CampaignOwnerType ownerType) { this.ownerType = ownerType; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
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
    public Boolean getLoyaltyBonusEnabled() { return loyaltyBonusEnabled; }
    public void setLoyaltyBonusEnabled(Boolean loyaltyBonusEnabled) { this.loyaltyBonusEnabled = loyaltyBonusEnabled; }
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
}
