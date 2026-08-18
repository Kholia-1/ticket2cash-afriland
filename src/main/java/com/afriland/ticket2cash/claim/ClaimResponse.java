package com.afriland.ticket2cash.claim;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Public representation of a claim.  Deliberately excludes cardHash: hashes
 * are internal correlation values and must never be sent to a browser.
 */
public class ClaimResponse {
    private Long id;
    private String claimReference;
    private String userId;
    private Long merchantId;
    private Long campaignId;
    private Long ticketId;
    private BigDecimal ticketAmount;
    private BigDecimal cashbackAmount;
    private String maskedCard;
    private ClaimStatus status;
    private LocalDateTime submittedAt;
    private Integer fraudScore;
    private String reviewNotes;

    public static ClaimResponse from(Claim claim) {
        ClaimResponse out = new ClaimResponse();
        out.id = claim.getId();
        out.claimReference = claim.getClaimReference();
        out.userId = claim.getUserId();
        out.merchantId = claim.getMerchantId();
        out.campaignId = claim.getCampaignId();
        out.ticketId = claim.getTicketId();
        out.ticketAmount = claim.getTicketAmount();
        out.cashbackAmount = claim.getCashbackAmount();
        out.maskedCard = claim.getMaskedCard();
        out.status = claim.getStatus();
        out.submittedAt = claim.getSubmittedAt();
        out.fraudScore = claim.getFraudScore();
        out.reviewNotes = claim.getReviewNotes();
        return out;
    }

    public Long getId() { return id; }
    public String getClaimReference() { return claimReference; }
    public String getUserId() { return userId; }
    public Long getMerchantId() { return merchantId; }
    public Long getCampaignId() { return campaignId; }
    public Long getTicketId() { return ticketId; }
    public BigDecimal getTicketAmount() { return ticketAmount; }
    public BigDecimal getCashbackAmount() { return cashbackAmount; }
    public String getMaskedCard() { return maskedCard; }
    public ClaimStatus getStatus() { return status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public Integer getFraudScore() { return fraudScore; }
    public String getReviewNotes() { return reviewNotes; }
}
