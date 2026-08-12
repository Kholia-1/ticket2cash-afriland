package com.afriland.ticket2cash.loyalty;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A bank client of Afriland enrolled in the loyalty cashback program.
 * Distinct from the {@code Merchant} / mobile app enrollment. A client here
 * is identified by their bank account number (unique) and may optionally be
 * linked to a prepaid card or a mobile phone for notifications.
 */
@Entity
@Table(name = "loyalty_clients", indexes = {
        @Index(name = "idx_loyalty_client_account", columnList = "accountNumber", unique = true),
        @Index(name = "idx_loyalty_client_phone", columnList = "phone")
})
public class LoyaltyClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bank account number — unique, key used to reconcile with uploaded transactions. */
    @Column(nullable = false, length = 40)
    private String accountNumber;

    private String fullName;
    private String phone;
    private String email;

    /** Prepaid card number used to credit cashback (16 digits, optional). */
    @Column(length = 20)
    private String cardNumber;

    /** Segmentation tier — CLASSIC / SILVER / GOLD / PLATINUM. */
    @Column(length = 20)
    private String tier;

    /**
     * Legal entity type — INDIVIDUAL (particulier) or COMPANY (entreprise).
     * Drives the segmented view on the Business Dashboard and lets admins
     * upload mixed batches without pre-splitting the file.
     */
    @Column(length = 15)
    private String entityType;

    /**
     * Date the account was opened at Afriland.
     * Used by rules with {@code minAccountAgeMonths} to reward loyalty.
     * Set from bank data during client onboarding; null-safe in the calculator.
     */
    private LocalDate accountOpenedDate;

    /** City / branch — used for geographic analytics. */
    private String city;
    private String branch;

    /** Aggregate lifetime cashback earned (updated after each batch). */
    @Column(precision = 15, scale = 2)
    private BigDecimal lifetimeCashback = BigDecimal.ZERO;

    /** Aggregate lifetime volume (sum of qualifying transactions). */
    @Column(precision = 18, scale = 2)
    private BigDecimal lifetimeVolume = BigDecimal.ZERO;

    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;

    public LoyaltyClient() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (lifetimeCashback == null) lifetimeCashback = BigDecimal.ZERO;
        if (lifetimeVolume == null) lifetimeVolume = BigDecimal.ZERO;
        if (tier == null || tier.isBlank()) tier = "CLASSIC";
        if (entityType == null || entityType.isBlank()) entityType = "INDIVIDUAL";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public LocalDate getAccountOpenedDate() { return accountOpenedDate; }
    public void setAccountOpenedDate(LocalDate accountOpenedDate) { this.accountOpenedDate = accountOpenedDate; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public BigDecimal getLifetimeCashback() { return lifetimeCashback; }
    public void setLifetimeCashback(BigDecimal v) { this.lifetimeCashback = v; }
    public BigDecimal getLifetimeVolume() { return lifetimeVolume; }
    public void setLifetimeVolume(BigDecimal v) { this.lifetimeVolume = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
}
