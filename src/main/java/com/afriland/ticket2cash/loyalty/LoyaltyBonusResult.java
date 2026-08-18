package com.afriland.ticket2cash.loyalty;

import java.math.BigDecimal;

public class LoyaltyBonusResult {
    private final String tierName;
    private final BigDecimal bonusPercent;
    private final BigDecimal bonusAmount;
    private final LoyaltyBonusDecisionCode decisionCode;
    private final String reason;

    private LoyaltyBonusResult(String tierName, BigDecimal bonusPercent, BigDecimal bonusAmount,
                               LoyaltyBonusDecisionCode decisionCode, String reason) {
        this.tierName = tierName;
        this.bonusPercent = bonusPercent;
        this.bonusAmount = bonusAmount;
        this.decisionCode = decisionCode;
        this.reason = reason;
    }

    public static LoyaltyBonusResult of(String tierName, BigDecimal percent, BigDecimal amount,
                                        LoyaltyBonusDecisionCode code, String reason) {
        return new LoyaltyBonusResult(tierName, percent, amount, code, reason);
    }

    public LoyaltyBonusResult withPercent(BigDecimal percent) {
        return new LoyaltyBonusResult(tierName, percent, bonusAmount, decisionCode, reason);
    }

    public String getTierName() { return tierName; }
    public BigDecimal getBonusPercent() { return bonusPercent; }
    public BigDecimal getBonusAmount() { return bonusAmount; }
    public LoyaltyBonusDecisionCode getDecisionCode() { return decisionCode; }
    public String getReason() { return reason; }
}
