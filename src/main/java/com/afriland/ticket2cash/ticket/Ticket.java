package com.afriland.ticket2cash.ticket;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long merchantId;
    private String ticketNumber;
    private String storeName;
    private LocalDateTime ticketDateTime;
    private BigDecimal totalAmount;
    private String currency;

    private String ticketHash;
    private Integer fraudScore;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    @Column(columnDefinition = "TEXT")
    private String ocrRawText;

    private LocalDateTime createdAt;

    public Ticket() {
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }

        if (this.status == null) {
            this.status = TicketStatus.OCR_PROCESSED;
        }

        if (this.currency == null) {
            this.currency = "FCFA";
        }

        if (this.fraudScore == null) {
            this.fraudScore = 0;
        }
    }

    public Long getId() { return id; }
    public Long getMerchantId() { return merchantId; }
    public String getTicketNumber() { return ticketNumber; }
    public String getStoreName() { return storeName; }
    public LocalDateTime getTicketDateTime() { return ticketDateTime; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getTicketHash() { return ticketHash; }
    public Integer getFraudScore() { return fraudScore; }
    public TicketStatus getStatus() { return status; }
    public String getOcrRawText() { return ocrRawText; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public void setTicketDateTime(LocalDateTime ticketDateTime) { this.ticketDateTime = ticketDateTime; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setTicketHash(String ticketHash) { this.ticketHash = ticketHash; }
    public void setFraudScore(Integer fraudScore) { this.fraudScore = fraudScore; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public void setOcrRawText(String ocrRawText) { this.ocrRawText = ocrRawText; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}