package com.afriland.ticket2cash.clo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Cardholder opt-in for the CLO programme (GL-04).
 *
 * <p>One record per bank account number. A cardholder must opt in explicitly
 * before receiving CLO offers or having their transactions analysed for
 * targeting purposes.
 *
 * <p>Opt-in is revocable — {@link #revokedAt} is set when the cardholder or
 * admin revokes consent. A revoked opt-in stays in the database for audit;
 * new opt-ins overwrite the record.
 */
@Entity
@Table(name = "clo_opt_ins", indexes = {
        @Index(name = "idx_clo_optin_acc", columnList = "accountNumber", unique = true)
})
public class CloOptIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bank account number of the cardholder. Unique. */
    @Column(nullable = false, length = 40, unique = true)
    private String accountNumber;

    /** Owner name snapshot (for display convenience). */
    @Column(length = 200)
    private String ownerName;

    /** True while consent is active. */
    @Column(nullable = false)
    private Boolean optedIn;

    /** When the customer opted in. */
    private LocalDateTime optedInAt;

    /** When (if) consent was revoked. */
    private LocalDateTime revokedAt;

    /**
     * Notification preferences: comma-separated set of PUSH, SMS, EMAIL.
     * Default "PUSH,SMS".
     */
    @Column(length = 100)
    private String notificationChannels;

    /** Who acted on this record (username, or 'CUSTOMER'). */
    private String actionBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CloOptIn() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (optedIn == null) optedIn = false;
        if (notificationChannels == null) notificationChannels = "PUSH,SMS";
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
    public Boolean getOptedIn() { return optedIn; }
    public void setOptedIn(Boolean optedIn) { this.optedIn = optedIn; }
    public LocalDateTime getOptedInAt() { return optedInAt; }
    public void setOptedInAt(LocalDateTime optedInAt) { this.optedInAt = optedInAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getNotificationChannels() { return notificationChannels; }
    public void setNotificationChannels(String v) { this.notificationChannels = v; }
    public String getActionBy() { return actionBy; }
    public void setActionBy(String actionBy) { this.actionBy = actionBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
