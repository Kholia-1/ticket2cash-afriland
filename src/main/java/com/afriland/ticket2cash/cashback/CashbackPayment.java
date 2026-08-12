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
    public void setMaskedCard(String maskedCard) { this.maskedCard = maskedCard; }
    public void setCardBin(String cardBin) { this.cardBin = cardBin; }
    public void setTransactionDateTime(LocalDateTime transactionDateTime) { this.transactionDateTime = transactionDateTime; }
}
