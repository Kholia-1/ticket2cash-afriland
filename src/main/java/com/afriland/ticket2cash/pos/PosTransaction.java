package com.afriland.ticket2cash.pos;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores real POS transactions received via webhook from the bank.
 * Used to verify scanned receipts against actual card transactions.
 */
@Entity
@Table(name = "pos_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pos_transaction_ref", columnNames = "transaction_ref")
})
public class PosTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 120)
    private String transactionRef;      // Bank's unique transaction reference
    private String cardHash;            // SHA-256 hash of the card number
    private String maskedCard;          // Last 4 digits: ****1234
    private String cardBin;             // Non-sensitive BIN prefix only
    private String merchantName;        // POS merchant name
    private Long merchantId;            // Matched Ticket2Cash merchant ID
    private BigDecimal amount;          // Transaction amount
    private String currency;            // FCFA
    private String mccCode;
    private String channel;
    private String source;
    private String terminalId;
    private String status;
    private LocalDateTime transactionDate;  // When the transaction happened
    private LocalDateTime receivedAt;   // When webhook was received

    private boolean matched;            // Has this been matched to a scan?
    private Long matchedClaimId;        // Claim ID if matched

    @Enumerated(EnumType.STRING)
    private TransactionWorkflowStatus workflowStatus;
    private String currentStep;
    private boolean manualReviewRequired;
    private String validatedBy;
    private LocalDateTime validatedAt;
    @Column(length = 500)
    private String rejectionReason;
    @Column(length = 1000)
    private String lastWorkflowComment;

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
        if (currency == null) currency = "FCFA";
        if (workflowStatus == null) workflowStatus = TransactionWorkflowStatus.RECEIVED;
        if (currentStep == null) currentStep = workflowStatus.name();
    }

    // Getters
    public Long getId() { return id; }
    public String getTransactionRef() { return transactionRef; }
    public String getCardHash() { return cardHash; }
    public String getMaskedCard() { return maskedCard; }
    public String getCardBin() { return cardBin; }
    public String getMerchantName() { return merchantName; }
    public Long getMerchantId() { return merchantId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMccCode() { return mccCode; }
    public String getChannel() { return channel; }
    public String getSource() { return source; }
    public String getTerminalId() { return terminalId; }
    public String getStatus() { return status; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public boolean isMatched() { return matched; }
    public Long getMatchedClaimId() { return matchedClaimId; }
    public TransactionWorkflowStatus getWorkflowStatus() { return workflowStatus; }
    public String getCurrentStep() { return currentStep; }
    public boolean isManualReviewRequired() { return manualReviewRequired; }
    public String getValidatedBy() { return validatedBy; }
    public LocalDateTime getValidatedAt() { return validatedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getLastWorkflowComment() { return lastWorkflowComment; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public void setCardHash(String cardHash) { this.cardHash = cardHash; }
    public void setMaskedCard(String maskedCard) {
        if (maskedCard != null) {
            String compact = maskedCard.replaceAll("[ -]", "");
            if (compact.matches("\\d{13,19}")) maskedCard = "****" + compact.substring(compact.length() - 4);
        }
        this.maskedCard = maskedCard;
    }
    public void setCardBin(String cardBin) { this.cardBin = cardBin; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setMccCode(String mccCode) { this.mccCode = mccCode; }
    public void setChannel(String channel) { this.channel = channel; }
    public void setSource(String source) { this.source = source; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
    public void setStatus(String status) { this.status = status; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public void setMatched(boolean matched) { this.matched = matched; }
    public void setMatchedClaimId(Long matchedClaimId) { this.matchedClaimId = matchedClaimId; }
    public void setWorkflowStatus(TransactionWorkflowStatus workflowStatus) { this.workflowStatus = workflowStatus; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public void setManualReviewRequired(boolean manualReviewRequired) { this.manualReviewRequired = manualReviewRequired; }
    public void setValidatedBy(String validatedBy) { this.validatedBy = validatedBy; }
    public void setValidatedAt(LocalDateTime validatedAt) { this.validatedAt = validatedAt; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setLastWorkflowComment(String lastWorkflowComment) { this.lastWorkflowComment = lastWorkflowComment; }
}
