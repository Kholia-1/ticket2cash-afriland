package com.afriland.ticket2cash.loyalty;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The computed cashback line for one client within one batch.
 * (batchId, accountNumber) is unique.
 */
@Entity
@Table(name = "loyalty_results", indexes = {
        @Index(name = "idx_loyalty_result_batch", columnList = "batchId"),
        @Index(name = "idx_loyalty_result_account", columnList = "accountNumber"),
        @Index(name = "uk_loyalty_result_batch_account", columnList = "batchId,accountNumber", unique = true)
})
public class LoyaltyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false, length = 40)
    private String accountNumber;

    private String clientName;
    private String phone;
    private String cardNumber;
    private String tier;

    /** Snapshot of the client's entity type at calc time. */
    @Column(length = 15)
    private String entityType;

    private Integer transactionCount;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalVolume;

    @Column(precision = 15, scale = 2)
    private BigDecimal cashbackAmount;

    /** Effective percentage applied (for TIERED_VOLUME this is the resolved tier). */
    @Column(precision = 6, scale = 3)
    private BigDecimal effectiveRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoyaltyResultStatus status;

    /** Credit reference from Core Banking once credited. */
    private String creditReference;

    private LocalDateTime creditedAt;

    /** Free-form explanation, e.g. "Below minPeriodVolume threshold". */
    @Column(length = 500)
    private String note;

    public LoyaltyResult() {}

    @PrePersist
    public void prePersist() {
        if (status == null) status = LoyaltyResultStatus.PENDING;
        if (cashbackAmount == null) cashbackAmount = BigDecimal.ZERO;
        if (totalVolume == null) totalVolume = BigDecimal.ZERO;
        if (transactionCount == null) transactionCount = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Integer getTransactionCount() { return transactionCount; }
    public void setTransactionCount(Integer transactionCount) { this.transactionCount = transactionCount; }
    public BigDecimal getTotalVolume() { return totalVolume; }
    public void setTotalVolume(BigDecimal totalVolume) { this.totalVolume = totalVolume; }
    public BigDecimal getCashbackAmount() { return cashbackAmount; }
    public void setCashbackAmount(BigDecimal cashbackAmount) { this.cashbackAmount = cashbackAmount; }
    public BigDecimal getEffectiveRate() { return effectiveRate; }
    public void setEffectiveRate(BigDecimal effectiveRate) { this.effectiveRate = effectiveRate; }
    public LoyaltyResultStatus getStatus() { return status; }
    public void setStatus(LoyaltyResultStatus status) { this.status = status; }
    public String getCreditReference() { return creditReference; }
    public void setCreditReference(String creditReference) { this.creditReference = creditReference; }
    public LocalDateTime getCreditedAt() { return creditedAt; }
    public void setCreditedAt(LocalDateTime creditedAt) { this.creditedAt = creditedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
