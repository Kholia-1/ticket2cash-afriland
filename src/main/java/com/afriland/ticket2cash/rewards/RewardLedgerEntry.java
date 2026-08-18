package com.afriland.ticket2cash.rewards;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reward_ledger_entries", uniqueConstraints = @UniqueConstraint(
        name = "uk_reward_source_benefit_campaign",
        columnNames = {"source_type", "source_ref", "benefit_type", "campaign_id"}))
public class RewardLedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(name="source_type", nullable=false, length=50)
    private RewardSourceType sourceType;
    @Column(name="source_ref", nullable=false, length=160) private String sourceRef;
    @Column(name="customer_ref", length=160) private String customerRef;
    @Column(name="masked_card", length=32) private String maskedCard;
    @Column(name="card_hash", length=255) private String cardHash;
    private Long merchantId;
    @Column(length=255) private String merchantName;
    private Long campaignId;
    @Enumerated(EnumType.STRING) @Column(name="benefit_type", nullable=false, length=50)
    private RewardBenefitType benefitType;
    private BigDecimal cashbackAmount;
    private Long pointsAmount;
    @Column(length=255) private String voucherCode;
    @Column(nullable=false, length=30) private String status;
    @Enumerated(EnumType.STRING) @Column(name="decision_code", length=60)
    private RewardDecisionCode decisionCode;
    @Column(length=500) private String rejectionReason;
    private LocalDateTime calculatedAt;
    private LocalDateTime postedAt;
    @Column(name="details_json", columnDefinition="CLOB") private String detailsJson;

    @PrePersist
    void prePersist() { if (calculatedAt == null) calculatedAt = LocalDateTime.now(); if (status == null) status = "CALCULATED"; }

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public RewardSourceType getSourceType(){return sourceType;} public void setSourceType(RewardSourceType v){sourceType=v;}
    public String getSourceRef(){return sourceRef;} public void setSourceRef(String v){sourceRef=v;}
    public String getCustomerRef(){return customerRef;} public void setCustomerRef(String v){customerRef=v;}
    public String getMaskedCard(){return maskedCard;} public void setMaskedCard(String v){maskedCard=v;}
    public String getCardHash(){return cardHash;} public void setCardHash(String v){cardHash=v;}
    public Long getMerchantId(){return merchantId;} public void setMerchantId(Long v){merchantId=v;}
    public String getMerchantName(){return merchantName;} public void setMerchantName(String v){merchantName=v;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long v){campaignId=v;}
    public RewardBenefitType getBenefitType(){return benefitType;} public void setBenefitType(RewardBenefitType v){benefitType=v;}
    public BigDecimal getCashbackAmount(){return cashbackAmount;} public void setCashbackAmount(BigDecimal v){cashbackAmount=v;}
    public Long getPointsAmount(){return pointsAmount;} public void setPointsAmount(Long v){pointsAmount=v;}
    public String getVoucherCode(){return voucherCode;} public void setVoucherCode(String v){voucherCode=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public RewardDecisionCode getDecisionCode(){return decisionCode;} public void setDecisionCode(RewardDecisionCode v){decisionCode=v;}
    public String getRejectionReason(){return rejectionReason;} public void setRejectionReason(String v){rejectionReason=v;}
    public LocalDateTime getCalculatedAt(){return calculatedAt;} public void setCalculatedAt(LocalDateTime v){calculatedAt=v;}
    public LocalDateTime getPostedAt(){return postedAt;} public void setPostedAt(LocalDateTime v){postedAt=v;}
    public String getDetailsJson(){return detailsJson;} public void setDetailsJson(String v){detailsJson=v;}
}
