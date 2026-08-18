package com.afriland.ticket2cash.cashback;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cashback_payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cashback_payment_transaction_ref", columnNames = "transaction_ref")
})
public class CashbackPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String paymentReference;
    @Column(name = "transaction_ref", unique = true, length = 120)
    private String transactionRef;
    private Long claimId;
    private Long merchantId;
    private Long campaignId;
    private String userId;

    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private CashbackPaymentStatus status;

    private LocalDateTime processedAt;
    private String cardHash;
    private String maskedCard;
    private String cardBin;
    private LocalDateTime transactionDateTime;

    @Enumerated(EnumType.STRING)
    private CashbackCreditStatus creditStatus;
    private String creditReference;
    private LocalDateTime creditedAt;
    @Column(length = 2000)
    private String creditFailureReason;
    private String prepaidAccountRef;
    private String customerRef;

    /** Decomposition of the final amount for audit and reconciliation. */
    private BigDecimal campaignCashbackAmount;
    private Boolean loyaltyBonusEnabled;
    private String loyaltyTierName;
    private BigDecimal loyaltyBonusPercent;
    private BigDecimal loyaltyBonusAmount;
    private BigDecimal finalCashbackAmount;

    public CashbackPayment() {
    }

    @PrePersist
    public void prePersist() {
        if (this.processedAt == null) {
            this.processedAt = LocalDateTime.now();
        }

        if (this.currency == null) {
            this.currency = "FCFA";
        }

        if (this.status == null) {
            this.status = CashbackPaymentStatus.PENDING;
        }
        if (this.creditStatus == null && (this.status == CashbackPaymentStatus.PENDING || this.status == CashbackPaymentStatus.SUCCESS)) {
            this.creditStatus = CashbackCreditStatus.CREDIT_PENDING;
        }
    }

    public Long getId() { return id; }
    public String getPaymentReference() { return paymentReference; }
    public String getTransactionRef() { return transactionRef; }
    public Long getClaimId() { return claimId; }
    public Long getMerchantId() { return merchantId; }
    public Long getCampaignId() { return campaignId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public CashbackPaymentStatus getStatus() { return status; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public String getCardHash() { return cardHash; }
    public String getMaskedCard() { return maskedCard; }
    public String getCardBin() { return cardBin; }
    public LocalDateTime getTransactionDateTime() { return transactionDateTime; }
    public CashbackCreditStatus getCreditStatus() { return creditStatus; }
    public String getCreditReference() { return creditReference; }
    public LocalDateTime getCreditedAt() { return creditedAt; }
    public String getCreditFailureReason() { return creditFailureReason; }
    public String getPrepaidAccountRef() { return prepaidAccountRef; }
    public String getCustomerRef() { return customerRef; }
    public BigDecimal getCampaignCashbackAmount() { return campaignCashbackAmount; }
    public Boolean getLoyaltyBonusEnabled() { return loyaltyBonusEnabled; }
    public String getLoyaltyTierName() { return loyaltyTierName; }
    public BigDecimal getLoyaltyBonusPercent() { return loyaltyBonusPercent; }
    public BigDecimal getLoyaltyBonusAmount() { return loyaltyBonusAmount; }
    public BigDecimal getFinalCashbackAmount() { return finalCashbackAmount; }

    public void setId(Long id) { this.id = id; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setStatus(CashbackPaymentStatus status) { this.status = status; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public void setCardHash(String cardHash) { this.cardHash = cardHash; }
    public void setMaskedCard(String maskedCard) {
        if (maskedCard != null) {
            String compact = maskedCard.replaceAll("[ -]", "");
            if (compact.matches("\\d{13,19}")) maskedCard = "****" + compact.substring(compact.length() - 4);
        }
        this.maskedCard = maskedCard;
    }
    public void setCardBin(String cardBin) { this.cardBin = cardBin; }
    public void setTransactionDateTime(LocalDateTime transactionDateTime) { this.transactionDateTime = transactionDateTime; }
    public void setCreditStatus(CashbackCreditStatus creditStatus) { this.creditStatus = creditStatus; }
    public void setCreditReference(String creditReference) { this.creditReference = creditReference; }
    public void setCreditedAt(LocalDateTime creditedAt) { this.creditedAt = creditedAt; }
    public void setCreditFailureReason(String creditFailureReason) { this.creditFailureReason = creditFailureReason; }
    public void setPrepaidAccountRef(String prepaidAccountRef) { this.prepaidAccountRef = prepaidAccountRef; }
    public void setCustomerRef(String customerRef) { this.customerRef = customerRef; }
    public void setCampaignCashbackAmount(BigDecimal value) { this.campaignCashbackAmount = value; }
    public void setLoyaltyBonusEnabled(Boolean value) { this.loyaltyBonusEnabled = value; }
    public void setLoyaltyTierName(String value) { this.loyaltyTierName = value; }
    public void setLoyaltyBonusPercent(BigDecimal value) { this.loyaltyBonusPercent = value; }
    public void setLoyaltyBonusAmount(BigDecimal value) { this.loyaltyBonusAmount = value; }
    public void setFinalCashbackAmount(BigDecimal value) { this.finalCashbackAmount = value; }
}
