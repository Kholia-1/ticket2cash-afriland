package com.afriland.ticket2cash.points;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A points account — one per loyalty client (referenced by accountNumber).
 * GL-02 in the strategic cahier.
 *
 * <p>Auto-created on the first earn transaction if it doesn't already exist.
 * Maintains a running balance denormalized from the transaction log for quick
 * reads; the source of truth remains {@link PointsTransaction}.
 */
@Entity
@Table(name = "points_accounts", indexes = {
        @Index(name = "idx_pts_acc_number", columnList = "accountNumber", unique = true)
})
public class PointsAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The bank account number this points account belongs to. Unique. */
    @Column(nullable = false, length = 40, unique = true)
    private String accountNumber;

    /** Snapshot of the client's name at last update (for UI convenience). */
    @Column(length = 200)
    private String ownerName;

    /** Running balance. Kept in sync with {@link PointsTransaction} deltas. */
    @Column(nullable = false)
    private Long balance;

    /** Cumulative earned points over the account's lifetime. */
    @Column(nullable = false)
    private Long totalEarned;

    /** Cumulative burned points over the account's lifetime. */
    @Column(nullable = false)
    private Long totalBurned;

    /** Cumulative expired points over the account's lifetime. */
    @Column(nullable = false)
    private Long totalExpired;

    /**
     * Timestamp of the last successful expiration sweep for this account.
     * Used by the expiration job to avoid re-processing.
     */
    private LocalDateTime lastExpirationCheck;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PointsAccount() {}

    public PointsAccount(String accountNumber, String ownerName) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (balance == null) balance = 0L;
        if (totalEarned == null) totalEarned = 0L;
        if (totalBurned == null) totalBurned = 0L;
        if (totalExpired == null) totalExpired = 0L;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Long getBalance() { return balance; }
    public void setBalance(Long balance) { this.balance = balance; }
    public Long getTotalEarned() { return totalEarned; }
    public void setTotalEarned(Long totalEarned) { this.totalEarned = totalEarned; }
    public Long getTotalBurned() { return totalBurned; }
    public void setTotalBurned(Long totalBurned) { this.totalBurned = totalBurned; }
    public Long getTotalExpired() { return totalExpired; }
    public void setTotalExpired(Long totalExpired) { this.totalExpired = totalExpired; }
    public LocalDateTime getLastExpirationCheck() { return lastExpirationCheck; }
    public void setLastExpirationCheck(LocalDateTime v) { this.lastExpirationCheck = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
