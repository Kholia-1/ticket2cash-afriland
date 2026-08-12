package com.afriland.ticket2cash.loyalty;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row from an uploaded bank-transaction history file. Belongs to exactly
 * one {@link LoyaltyCashbackBatch}. Transactions from the same physical file
 * are grouped by the batch — this makes re-processing / rollback trivial and
 * prevents cross-batch double-counting.
 */
@Entity
@Table(name = "loyalty_transactions", indexes = {
        @Index(name = "idx_loyalty_tx_batch", columnList = "batchId"),
        @Index(name = "idx_loyalty_tx_account", columnList = "accountNumber"),
        @Index(name = "idx_loyalty_tx_ref", columnList = "referenceNumber")
})
public class LoyaltyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to LoyaltyCashbackBatch — the upload that produced this row. */
    @Column(nullable = false)
    private Long batchId;

    /** Bank account number of the client who made the transaction. */
    @Column(nullable = false, length = 40)
    private String accountNumber;

    /** Client full name as it appeared in the file (denormalized for audit). */
    private String clientName;

    /** Transaction date (as parsed from the file). */
    @Column(nullable = false)
    private LocalDate transactionDate;

    /** Signed amount in FCFA. Positive = credit to client, negative = debit. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** Free-form description from the bank statement. */
    @Column(length = 500)
    private String description;

    /**
     * Category label — used by CATEGORY_BASED rules. Optional. Examples:
     * TRANSFER, WITHDRAWAL, DEPOSIT, PAYMENT, LOAN_REPAYMENT, CARD_PURCHASE.
     */
    @Column(length = 50)
    private String category;

    /** Reference number from bank statement — used for duplicate detection. */
    @Column(length = 80)
    private String referenceNumber;

    /** Currency, defaults to FCFA. */
    @Column(length = 5)
    private String currency;

    /** Whether this transaction qualified for cashback in the batch. */
    private Boolean qualified;

    /** Cashback amount computed for this transaction (may be 0 if not qualified). */
    @Column(precision = 15, scale = 2)
    private BigDecimal cashbackAmount;

    private LocalDateTime importedAt;

    /**
     * Transient entity-type hint parsed from the file row (INDIVIDUAL / COMPANY).
     * Used by the import service to set/refresh the {@link LoyaltyClient#entityType}
     * for the row's owner; never persisted on the transaction itself.
     */
    @Transient
    private String importedEntityType;

    public LoyaltyTransaction() {}

    @PrePersist
    public void prePersist() {
        if (importedAt == null) importedAt = LocalDateTime.now();
        if (currency == null) currency = "FCFA";
        if (qualified == null) qualified = false;
        if (cashbackAmount == null) cashbackAmount = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Boolean getQualified() { return qualified; }
    public void setQualified(Boolean qualified) { this.qualified = qualified; }
    public BigDecimal getCashbackAmount() { return cashbackAmount; }
    public void setCashbackAmount(BigDecimal cashbackAmount) { this.cashbackAmount = cashbackAmount; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public String getImportedEntityType() { return importedEntityType; }
    public void setImportedEntityType(String importedEntityType) { this.importedEntityType = importedEntityType; }
}
