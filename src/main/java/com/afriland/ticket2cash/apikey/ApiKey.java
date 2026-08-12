package com.afriland.ticket2cash.apikey;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * An API key issued to a partner (e.g. a supermarket) so their system can
 * push live purchase transactions into Ticket2Cash.
 * Only a HASH of the key is stored: the plain key is shown once, at creation.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;          // e.g. "Carrefour PlaYce - caisse"
    private Long merchantId;      // which partner this key belongs to

    @Column(unique = true)
    private String keyHash;       // sha256 of the secret key

    private String keyPrefix;     // first 8 chars, shown in the list (t2c_ab12cd..)

    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private Long callCount = 0L;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Long getCallCount() { return callCount; }
    public void setCallCount(Long callCount) { this.callCount = callCount; }
}
