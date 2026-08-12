package com.afriland.ticket2cash.claim;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String claimReference;
    private String userId;

    private Long merchantId;
    private Long campaignId;
    private Long ticketId;

    private BigDecimal ticketAmount;
    private BigDecimal cashbackAmount;

    private String maskedCard;
    private String cardHash;

    @Enumerated(EnumType.STRING)
    private ClaimStatus status;

    private LocalDateTime submittedAt;

    // Anti-fraud verdict data (set at verification time)
    private Integer fraudScore;

    @Column(length = 1000)
    private String reviewNotes;

    public Claim() {
    }

    @PrePersist
    public void prePersist() {
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }

        if (this.status == null) {
            this.status = ClaimStatus.SUBMITTED;
        }
    }

    public Long getId() { return id; }
    public String getClaimReference() { return claimReference; }
    public String getUserId() { return userId; }
    public Long getMerchantId() { return merchantId; }
    public Long getCampaignId() { return campaignId; }
    public Long getTicketId() { return ticketId; }
    public BigDecimal getTicketAmount() { return ticketAmount; }
    public BigDecimal getCashbackAmount() { return cashbackAmount; }
    public ClaimStatus getStatus() { return status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public String getMaskedCard() { return maskedCard; }
    public String getCardHash() { return cardHash; }

    public void setId(Long id) { this.id = id; }
    public void setClaimReference(String claimReference) { this.claimReference = claimReference; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public void setMaskedCard(String maskedCard) { this.maskedCard = maskedCard; }
    public void setCardHash(String cardHash) { this.cardHash = cardHash; }
    public void setTicketAmount(BigDecimal ticketAmount) { this.ticketAmount = ticketAmount; }
    public void setCashbackAmount(BigDecimal cashbackAmount) { this.cashbackAmount = cashbackAmount; }
    public void setStatus(ClaimStatus status) { this.status = status; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public Integer getFraudScore() { return fraudScore; }
    public void setFraudScore(Integer fraudScore) { this.fraudScore = fraudScore; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
}
