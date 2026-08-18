package com.afriland.ticket2cash.rewards;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Normalized input shared by card cashback and loyalty calculations. */
public class RewardCalculationContext {
    private RewardSourceType sourceType;
    private String sourceRef;
    private String customerRef;
    private String cardHash;
    private String maskedCard;
    private Long merchantId;
    private String merchantName;
    private String mccCode;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionDate;
    private String channel;
    private Long campaignId;

    /** Optional rule parameters used by the common engine. */
    private RewardBenefitType benefitType;
    private BigDecimal benefitValue;
    private Long pointsPer1000;
    private Long bonusPoints;
    private String voucherCode;
    private String detailsJson;

    public RewardSourceType getSourceType() { return sourceType; }
    public void setSourceType(RewardSourceType v) { sourceType = v; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String v) { sourceRef = v; }
    public String getCustomerRef() { return customerRef; }
    public void setCustomerRef(String v) { customerRef = v; }
    public String getCardHash() { return cardHash; }
    public void setCardHash(String v) { cardHash = v; }
    public String getMaskedCard() { return maskedCard; }
    public void setMaskedCard(String v) { maskedCard = v; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long v) { merchantId = v; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String v) { merchantName = v; }
    public String getMccCode() { return mccCode; }
    public void setMccCode(String v) { mccCode = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime v) { transactionDate = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { channel = v; }
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long v) { campaignId = v; }
    public RewardBenefitType getBenefitType() { return benefitType; }
    public void setBenefitType(RewardBenefitType v) { benefitType = v; }
    public BigDecimal getBenefitValue() { return benefitValue; }
    public void setBenefitValue(BigDecimal v) { benefitValue = v; }
    public Long getPointsPer1000() { return pointsPer1000; }
    public void setPointsPer1000(Long v) { pointsPer1000 = v; }
    public Long getBonusPoints() { return bonusPoints; }
    public void setBonusPoints(Long v) { bonusPoints = v; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String v) { voucherCode = v; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String v) { detailsJson = v; }
}
