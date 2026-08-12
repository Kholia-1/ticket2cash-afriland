package com.afriland.ticket2cash.request;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A message from a partner merchant to Afriland administrators.
 * Used for:
 * <ul>
 *   <li>Requesting a new cashback campaign (CAMPAIGN_REQUEST)</li>
 *   <li>Asking a question about the platform (QUESTION)</li>
 *   <li>Reporting a support issue (SUPPORT)</li>
 *   <li>Anything else (OTHER)</li>
 * </ul>
 *
 * <p>The partner writes a subject + message. Admins see it in their inbox,
 * can respond, and change the status. Threading (multiple back-and-forth) is
 * NOT modeled here — kept intentionally simple for v1.
 */
@Entity
@Table(name = "partner_requests")
public class PartnerRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Merchant who sent the request. Never null. */
    @Column(nullable = false)
    private Long merchantId;

    /** Merchant name at time of sending (snapshot for admin display). */
    @Column(length = 200)
    private String merchantName;

    /** Username of the sender (partner user). */
    @Column(length = 60)
    private String senderUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PartnerRequestType type;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerRequestStatus status;

    /** Admin's response text — plain text, may be null. */
    @Column(length = 4000)
    private String adminResponse;

    /** Username of the admin who responded. */
    @Column(length = 60)
    private String responderUsername;

    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private LocalDateTime updatedAt;

    public PartnerRequest() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = PartnerRequestStatus.OPEN;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters/Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public PartnerRequestType getType() { return type; }
    public void setType(PartnerRequestType type) { this.type = type; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public PartnerRequestStatus getStatus() { return status; }
    public void setStatus(PartnerRequestStatus status) { this.status = status; }
    public String getAdminResponse() { return adminResponse; }
    public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }
    public String getResponderUsername() { return responderUsername; }
    public void setResponderUsername(String responderUsername) { this.responderUsername = responderUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
