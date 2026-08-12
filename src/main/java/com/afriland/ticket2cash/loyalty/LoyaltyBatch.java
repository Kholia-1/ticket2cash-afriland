package com.afriland.ticket2cash.loyalty;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One batch = one upload of a transaction file + one applied rule + the set of
 * per-client results. The batch is the unit of audit, approval, and reversal.
 */
@Entity
@Table(name = "loyalty_batches")
public class LoyaltyBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human label, e.g. "Cashback Juillet 2026 — Clients GOLD". */
    @Column(nullable = false, length = 200)
    private String name;

    /** Original uploaded filename (for traceability). */
    private String sourceFilename;

    /** Rule that will be / was applied to this batch. */
    private Long ruleId;

    private String ruleNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoyaltyBatchStatus status;

    private Integer totalRows;
    private Integer parsedRows;
    private Integer failedRows;
    private Integer qualifiedRows;
    private Integer clientCount;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalVolume;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalCashback;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime calculatedAt;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private LocalDateTime creditedAt;

    /** Free-form error / status note. */
    @Column(length = 1000)
    private String note;

    public LoyaltyBatch() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = LoyaltyBatchStatus.IMPORTED;
        if (totalVolume == null) totalVolume = BigDecimal.ZERO;
        if (totalCashback == null) totalCashback = BigDecimal.ZERO;
        if (totalRows == null) totalRows = 0;
        if (parsedRows == null) parsedRows = 0;
        if (failedRows == null) failedRows = 0;
        if (qualifiedRows == null) qualifiedRows = 0;
        if (clientCount == null) clientCount = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleNameSnapshot() { return ruleNameSnapshot; }
    public void setRuleNameSnapshot(String ruleNameSnapshot) { this.ruleNameSnapshot = ruleNameSnapshot; }
    public LoyaltyBatchStatus getStatus() { return status; }
    public void setStatus(LoyaltyBatchStatus status) { this.status = status; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getParsedRows() { return parsedRows; }
    public void setParsedRows(Integer parsedRows) { this.parsedRows = parsedRows; }
    public Integer getFailedRows() { return failedRows; }
    public void setFailedRows(Integer failedRows) { this.failedRows = failedRows; }
    public Integer getQualifiedRows() { return qualifiedRows; }
    public void setQualifiedRows(Integer qualifiedRows) { this.qualifiedRows = qualifiedRows; }
    public Integer getClientCount() { return clientCount; }
    public void setClientCount(Integer clientCount) { this.clientCount = clientCount; }
    public BigDecimal getTotalVolume() { return totalVolume; }
    public void setTotalVolume(BigDecimal totalVolume) { this.totalVolume = totalVolume; }
    public BigDecimal getTotalCashback() { return totalCashback; }
    public void setTotalCashback(BigDecimal totalCashback) { this.totalCashback = totalCashback; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getCreditedAt() { return creditedAt; }
    public void setCreditedAt(LocalDateTime creditedAt) { this.creditedAt = creditedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
