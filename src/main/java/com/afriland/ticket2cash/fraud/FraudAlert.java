package com.afriland.ticket2cash.fraud;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String alertReference;
    private Long ticketId;
    private Long claimId;
    private Long merchantId;
    private String userId;

    private Integer riskScore;
    private String reason;

    @Enumerated(EnumType.STRING)
    private FraudAlertStatus status;

    private LocalDateTime createdAt;

    public FraudAlert() {
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }

        if (this.status == null) {
            this.status = FraudAlertStatus.OPEN;
        }
    }

    public Long getId() { return id; }
    public String getAlertReference() { return alertReference; }
    public Long getTicketId() { return ticketId; }
    public Long getClaimId() { return claimId; }
    public Long getMerchantId() { return merchantId; }
    public String getUserId() { return userId; }
    public Integer getRiskScore() { return riskScore; }
    public String getReason() { return reason; }
    public FraudAlertStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setAlertReference(String alertReference) { this.alertReference = alertReference; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public void setReason(String reason) { this.reason = reason; }
    public void setStatus(FraudAlertStatus status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}