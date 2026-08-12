package com.afriland.ticket2cash.points;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A single points ledger entry: earn, burn, expire, or adjust.
 * Immutable once created — the running balance in {@link PointsAccount} is
 * updated in the same transaction.
 */
@Entity
@Table(name = "points_transactions", indexes = {
        @Index(name = "idx_ptx_account", columnList = "accountNumber"),
        @Index(name = "idx_ptx_expires", columnList = "expiresAt"),
        @Index(name = "idx_ptx_type", columnList = "type")
})
public class PointsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Account this entry belongs to. */
    @Column(nullable = false, length = 40)
    private String accountNumber;

    /** Type of ledger movement. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointsTransactionType type;

    /**
     * The signed delta. Positive for EARN/ADJUST-up, negative for BURN/EXPIRE.
     * The absolute value is what the customer sees.
     */
    @Column(nullable = false)
    private Long points;

    /**
     * When this points batch expires (only for EARN entries).
     * BURN/EXPIRE/ADJUST leave this null.
     */
    private LocalDateTime expiresAt;

    /** Where these points came from: a loyalty transaction, a bonus, an adjustment. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PointsSource source;

    /**
     * Optional: link back to the loyalty transaction that generated this earn.
     * Null for manual adjustments and burns.
     */
    private Long sourceLoyaltyTransactionId;

    /** Optional: link back to the rule that computed this earn. Null for manual. */
    private Long sourcePointsRuleId;

    /** Human-readable reason, shown in customer history. */
    @Column(length = 500)
    private String description;

    /** Who created this entry (username of admin, or 'SYSTEM' for auto). */
    @Column(length = 100)
    private String createdBy;

    private LocalDateTime createdAt;

    public PointsTransaction() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public PointsTransactionType getType() { return type; }
    public void setType(PointsTransactionType type) { this.type = type; }
    public Long getPoints() { return points; }
    public void setPoints(Long points) { this.points = points; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public PointsSource getSource() { return source; }
    public void setSource(PointsSource source) { this.source = source; }
    public Long getSourceLoyaltyTransactionId() { return sourceLoyaltyTransactionId; }
    public void setSourceLoyaltyTransactionId(Long v) { this.sourceLoyaltyTransactionId = v; }
    public Long getSourcePointsRuleId() { return sourcePointsRuleId; }
    public void setSourcePointsRuleId(Long v) { this.sourcePointsRuleId = v; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
