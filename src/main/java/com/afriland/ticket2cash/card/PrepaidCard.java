package com.afriland.ticket2cash.card;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An Afriland prepaid card issued to a customer. Multiple cards can belong to
 * the same customer (identified by their bank account number).
 *
 * <p><b>Card numbers are stored masked</b> — only the first 6 digits (BIN) and
 * last 4 digits are kept in clear. The middle 6 digits are never stored.
 * The full card number never touches this system.
 *
 * <p>Each card is anchored to a bank account (mandatory per business rules)
 * and can be referenced for cashback filtering (via BIN), CLO offer targeting,
 * and loyalty attribution.
 */
@Entity
@Table(name = "prepaid_cards", indexes = {
        @Index(name = "idx_pc_owner", columnList = "ownerAccountNumber"),
        @Index(name = "idx_pc_bin", columnList = "bin"),
        @Index(name = "idx_pc_status", columnList = "status")
})
public class PrepaidCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Masked card number: {BIN}****{last4}. E.g. "456120****3742". */
    @Column(nullable = false, length = 30)
    private String cardNumberMasked;

    /** First 6 digits — the BIN. Used for CLO targeting. */
    @Column(nullable = false, length = 6)
    private String bin;

    /** Last 4 digits — for the user to recognise their card. */
    @Column(length = 4)
    private String last4;

    /** Product name resolved from the BIN, snapshotted at creation. */
    @Column(length = 100)
    private String productName;

    /** Bank account number of the customer this card belongs to. */
    @Column(nullable = false, length = 40)
    private String ownerAccountNumber;

    /** Customer's name (for display), snapshotted from account. */
    @Column(length = 200)
    private String ownerName;

    /** Customer's phone number for notifications. */
    @Column(length = 40)
    private String ownerPhone;

    /** Optional email for notifications. */
    @Column(length = 200)
    private String ownerEmail;

    /**
     * Bank account this prepaid card debits from / is linked to.
     * Not always identical to ownerAccountNumber (may be a company account).
     */
    @Column(nullable = false, length = 40)
    private String linkedBankAccount;

    /** Current lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrepaidCardStatus status;

    /** Date of first activation. Null if not yet activated. */
    private LocalDate activatedAt;

    /** Card expiration date (physical card lifetime). */
    private LocalDate expiresAt;

    /** Free-form notes for admin use. */
    @Column(length = 500)
    private String notes;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PrepaidCard() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = PrepaidCardStatus.ACTIVE;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardNumberMasked() { return cardNumberMasked; }
    public void setCardNumberMasked(String cardNumberMasked) { this.cardNumberMasked = cardNumberMasked; }
    public String getBin() { return bin; }
    public void setBin(String bin) { this.bin = bin; }
    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getOwnerAccountNumber() { return ownerAccountNumber; }
    public void setOwnerAccountNumber(String ownerAccountNumber) { this.ownerAccountNumber = ownerAccountNumber; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getLinkedBankAccount() { return linkedBankAccount; }
    public void setLinkedBankAccount(String linkedBankAccount) { this.linkedBankAccount = linkedBankAccount; }
    public PrepaidCardStatus getStatus() { return status; }
    public void setStatus(PrepaidCardStatus status) { this.status = status; }
    public LocalDate getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDate activatedAt) { this.activatedAt = activatedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
