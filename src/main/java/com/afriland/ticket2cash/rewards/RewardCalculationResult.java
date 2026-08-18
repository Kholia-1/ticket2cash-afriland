package com.afriland.ticket2cash.rewards;

import java.math.BigDecimal;

public class RewardCalculationResult {
    private boolean eligible;
    private RewardDecisionCode decisionCode;
    private String message;
    private RewardBenefitType benefitType;
    private BigDecimal cashbackAmount;
    private Long pointsAmount;
    private String voucherCode;
    private RewardLedgerEntry ledgerEntry;

    public static RewardCalculationResult rejected(RewardDecisionCode code, String message, RewardBenefitType type) {
        RewardCalculationResult result = new RewardCalculationResult(); result.eligible=false;
        result.decisionCode=code; result.message=message; result.benefitType=type; return result;
    }
    public static RewardCalculationResult approved(RewardBenefitType type, BigDecimal cashback, Long points, String voucher, RewardLedgerEntry entry) {
        RewardCalculationResult result = new RewardCalculationResult(); result.eligible=true;
        result.decisionCode=RewardDecisionCode.APPROVED; result.message="Reward calculated";
        result.benefitType=type; result.cashbackAmount=cashback; result.pointsAmount=points; result.voucherCode=voucher; result.ledgerEntry=entry; return result;
    }
    public boolean isEligible(){return eligible;} public RewardDecisionCode getDecisionCode(){return decisionCode;}
    public String getMessage(){return message;} public RewardBenefitType getBenefitType(){return benefitType;}
    public BigDecimal getCashbackAmount(){return cashbackAmount;} public Long getPointsAmount(){return pointsAmount;}
    public String getVoucherCode(){return voucherCode;} public RewardLedgerEntry getLedgerEntry(){return ledgerEntry;}
}
