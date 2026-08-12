package com.afriland.ticket2cash.voucher;

import com.afriland.ticket2cash.merchant.Merchant;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A voucher / coupon delivered to a customer — GL-06 in the strategic cahier.
 *
 * <p>Vouchers are generated with a unique code and can be:
 * <ul>
 *   <li>Redeemed automatically (statement credit) when the customer pays</li>
 *   <li>Presented at the merchant (QR code / alphanumeric code)</li>
 * </ul>
 */
@Entity
@Table(name = "vouchers", indexes = {
        @Index(name = "idx_voucher_code", columnList = "code", unique = true),
        @Index(name = "idx_voucher_owner", columnList = "ownerAccountNumber")
})
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique alphanumeric code. QR data URI is generated on the fly. */
    @Column(nullable = false, length = 30, unique = true)
    private String code;

    /** Human-readable name shown to the customer. */
    @Column(nullable = false, length = 200)
    private String name;

    /** Optional description explaining the voucher's rules. */
    @Column(length = 500)
    private String description;

    /** How this voucher was generated (welcome, birthday, campaign, etc). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VoucherType type;

    /** Percentage discount / fixed amount / free product. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VoucherValueType valueType;

    /**
     * The numeric value: percentage points if PERCENTAGE_DISCOUNT, FCFA if FIXED_AMOUNT,
     * ignored if FREE_PRODUCT.
     *
     * <p>Column named {@code voucher_value} rather than {@code value} because
     * {@code VALUE} is a reserved SQL keyword in H2 2.x (and PostgreSQL). Using
     * the raw field name would break DDL generation.
     */
    @Column(name = "voucher_value", precision = 15, scale = 2)
    private BigDecimal value;

    /** Optional: which merchant this voucher is valid at. Null = any merchant. */
    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    /**
     * Bank account number of the customer who owns this voucher.
     * Null means "unassigned" (still in a pool, e.g. mass-generated codes).
     */
    @Column(length = 40)
    private String ownerAccountNumber;

    /** Free-text describing owner (name), snapshot at issuance. */
    @Column(length = 200)
    private String ownerName;

    private LocalDate validFrom;
    private LocalDate validTo;

    /** Single-use or multi-use. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherUsageMode usageMode;

    /** How many times consumed (starts at 0). */
    private Integer currentUses;

    /** For multi-use: total allowed uses. Null = unlimited. */
    private Integer maxUses;

    /** Lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status;

    /** Free-form notes visible to admin only. */
    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime consumedAt;
    private String createdBy;

    public Voucher() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = VoucherStatus.ISSUED;
        if (currentUses == null) currentUses = 0;
        if (usageMode == null) usageMode = VoucherUsageMode.SINGLE_USE;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public VoucherType getType() { return type; }
    public void setType(VoucherType type) { this.type = type; }
    public VoucherValueType getValueType() { return valueType; }
    public void setValueType(VoucherValueType valueType) { this.valueType = valueType; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public String getOwnerAccountNumber() { return ownerAccountNumber; }
    public void setOwnerAccountNumber(String ownerAccountNumber) { this.ownerAccountNumber = ownerAccountNumber; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public VoucherUsageMode getUsageMode() { return usageMode; }
    public void setUsageMode(VoucherUsageMode usageMode) { this.usageMode = usageMode; }
    public Integer getCurrentUses() { return currentUses; }
    public void setCurrentUses(Integer currentUses) { this.currentUses = currentUses; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public VoucherStatus getStatus() { return status; }
    public void setStatus(VoucherStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
