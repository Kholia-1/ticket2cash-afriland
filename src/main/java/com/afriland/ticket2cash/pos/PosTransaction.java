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
    private LocalDateTime transactionDate;  // When the transaction happened
    private LocalDateTime receivedAt;   // When webhook was received

    private boolean matched;            // Has this been matched to a scan?
    private Long matchedClaimId;        // Claim ID if matched

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
        if (currency == null) currency = "FCFA";
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
    public LocalDateTime getTransactionDate() { return transactionDate; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public boolean isMatched() { return matched; }
    public Long getMatchedClaimId() { return matchedClaimId; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public void setCardHash(String cardHash) { this.cardHash = cardHash; }
    public void setMaskedCard(String maskedCard) { this.maskedCard = maskedCard; }
    public void setCardBin(String cardBin) { this.cardBin = cardBin; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setMccCode(String mccCode) { this.mccCode = mccCode; }
    public void setChannel(String channel) { this.channel = channel; }
    public void setSource(String source) { this.source = source; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public void setMatched(boolean matched) { this.matched = matched; }
    public void setMatchedClaimId(Long matchedClaimId) { this.matchedClaimId = matchedClaimId; }
}
