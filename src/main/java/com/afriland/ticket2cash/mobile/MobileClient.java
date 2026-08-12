package com.afriland.ticket2cash.mobile;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_clients")
public class MobileClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String phone;

    private String fullName;
    private String maskedCard;
    private String cardHash;
    private String pinHash;

    private String tier;
    private Integer tierPoints;

    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public MobileClient() {}

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.active == null) this.active = true;
        if (this.tier == null) this.tier = "Bronze";
        if (this.tierPoints == null) this.tierPoints = 0;
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getFullName() { return fullName; }
    public String getMaskedCard() { return maskedCard; }
    public String getCardHash() { return cardHash; }
    public String getPinHash() { return pinHash; }
    public String getTier() { return tier; }
    public Integer getTierPoints() { return tierPoints; }
    public Boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }

    public void setId(Long id) { this.id = id; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setMaskedCard(String maskedCard) { this.maskedCard = maskedCard; }
    public void setCardHash(String cardHash) { this.cardHash = cardHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    public void setTier(String tier) { this.tier = tier; }
    public void setTierPoints(Integer tierPoints) { this.tierPoints = tierPoints; }
    public void setActive(Boolean active) { this.active = active; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
