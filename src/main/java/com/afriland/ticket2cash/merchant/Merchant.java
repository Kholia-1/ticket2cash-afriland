package com.afriland.ticket2cash.merchant;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String brandName;
    private String rccm;
    private String niu;
    private String phone;
    private String email;
    private String city;
    private String address;

    @Enumerated(EnumType.STRING)
    private MerchantStatus status;

    private LocalDateTime createdAt;

    // Parent partner (e.g. PlaYce) -- null for a top-level partner
    private Long parentMerchantId;

    public Merchant() {
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }

        if (this.status == null) {
            this.status = MerchantStatus.KYC_PENDING;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBrandName() {
        return brandName;
    }

    public String getRccm() {
        return rccm;
    }

    public String getNiu() {
        return niu;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public String getAddress() {
        return address;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public void setRccm(String rccm) {
        this.rccm = rccm;
    }

    public void setNiu(String niu) {
        this.niu = niu;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setStatus(MerchantStatus status) {
        this.status = status;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getParentMerchantId() {
        return parentMerchantId;
    }

    public void setParentMerchantId(Long parentMerchantId) {
        this.parentMerchantId = parentMerchantId;
    }
}
