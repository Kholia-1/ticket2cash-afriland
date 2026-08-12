package com.afriland.ticket2cash.clo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single successful CLO redemption. Created each time an eligible transaction
 * triggers an offer for an opted-in cardholder.
 *
 * <p>Immutable record — one row per triggered offer, per transaction.
 */
@Entity
@Table(name = "clo_redemptions", indexes = {
        @Index(name = "idx_cr_offer", columnList = "offer_id"),
        @Index(name = "idx_cr_account", columnList = "accountNumber"),
        @Index(name = "idx_cr_date", columnList = "redeemedAt")
})
public class CloRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private CloOffer offer;

    @Column(nullable = false, length = 40)
    private String accountNumber;

    @Column(length = 30)
    private String cardNumberMasked;

    @Column(length = 6)
    private String bin;

    /** Amount of the underlying transaction that triggered the redemption. */
    @Column(precision = 15, scale = 2)
    private BigDecimal transactionAmount;

    /** The reward computed and credited. */
    @Column(precision = 15, scale = 2)
    private BigDecimal rewardAmount;

    /** Snapshot of what type of reward was granted. */
    @Column(length = 30)
    private String rewardType;

    /** Free text describing the trigger context. */
    @Column(length = 500)
    private String notes;

    private LocalDateTime redeemedAt;

    public CloRedemption() {}

    @PrePersist
    public void prePersist() {
        if (redeemedAt == null) redeemedAt = LocalDateTime.now();
    }

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CloOffer getOffer() { return offer; }
    public void setOffer(CloOffer offer) { this.offer = offer; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getCardNumberMasked() { return cardNumberMasked; }
    public void setCardNumberMasked(String cardNumberMasked) { this.cardNumberMasked = cardNumberMasked; }
    public String getBin() { return bin; }
    public void setBin(String bin) { this.bin = bin; }
    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
    public BigDecimal getRewardAmount() { return rewardAmount; }
    public void setRewardAmount(BigDecimal rewardAmount) { this.rewardAmount = rewardAmount; }
    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(LocalDateTime redeemedAt) { this.redeemedAt = redeemedAt; }
}
