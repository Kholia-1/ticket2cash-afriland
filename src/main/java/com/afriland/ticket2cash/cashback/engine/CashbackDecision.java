package com.afriland.ticket2cash.cashback.engine;

import java.math.BigDecimal;

public class CashbackDecision {

    private boolean eligible;
    private CashbackDecisionCode code;
    private String message;
    private BigDecimal ticketAmount;
    private BigDecimal calculatedCashback;
    private BigDecimal finalCashback;
    private BigDecimal dailyRemaining;
    private BigDecimal monthlyRemaining;
    private BigDecimal campaignBudgetRemaining;
    private Integer fraudScore;
    private Long campaignId;
    private Long merchantId;
    private Long ticketId;

    public static CashbackDecision approved(CashbackDecisionCode code, String message,
                                            BigDecimal ticketAmount, BigDecimal calculatedCashback,
                                            Integer fraudScore, Long campaignId, Long merchantId,
                                            Long ticketId) {
        CashbackDecision decision = new CashbackDecision();
        decision.eligible = true;
        decision.code = code;
        decision.message = message;
        decision.ticketAmount = ticketAmount;
        decision.calculatedCashback = calculatedCashback;
        decision.finalCashback = calculatedCashback;
        decision.fraudScore = fraudScore;
        decision.campaignId = campaignId;
        decision.merchantId = merchantId;
        decision.ticketId = ticketId;
        return decision;
    }

    public static CashbackDecision rejected(CashbackDecisionCode code, String message,
                                            BigDecimal ticketAmount, Integer fraudScore,
                                            Long campaignId, Long merchantId, Long ticketId) {
        CashbackDecision decision = new CashbackDecision();
        decision.eligible = false;
        decision.code = code;
        decision.message = message;
        decision.ticketAmount = ticketAmount;
        decision.calculatedCashback = BigDecimal.ZERO;
        decision.finalCashback = BigDecimal.ZERO;
        decision.fraudScore = fraudScore;
        decision.campaignId = campaignId;
        decision.merchantId = merchantId;
        decision.ticketId = ticketId;
        return decision;
    }

    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }
    public CashbackDecisionCode getCode() { return code; }
    public void setCode(CashbackDecisionCode code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public BigDecimal getTicketAmount() { return ticketAmount; }
    public void setTicketAmount(BigDecimal ticketAmount) { this.ticketAmount = ticketAmount; }
    public BigDecimal getCalculatedCashback() { return calculatedCashback; }
    public void setCalculatedCashback(BigDecimal calculatedCashback) { this.calculatedCashback = calculatedCashback; }
    public BigDecimal getFinalCashback() { return finalCashback; }
    public void setFinalCashback(BigDecimal finalCashback) { this.finalCashback = finalCashback; }
    public BigDecimal getDailyRemaining() { return dailyRemaining; }
    public void setDailyRemaining(BigDecimal dailyRemaining) { this.dailyRemaining = dailyRemaining; }
    public BigDecimal getMonthlyRemaining() { return monthlyRemaining; }
    public void setMonthlyRemaining(BigDecimal monthlyRemaining) { this.monthlyRemaining = monthlyRemaining; }
    public BigDecimal getCampaignBudgetRemaining() { return campaignBudgetRemaining; }
    public void setCampaignBudgetRemaining(BigDecimal campaignBudgetRemaining) { this.campaignBudgetRemaining = campaignBudgetRemaining; }
    public Integer getFraudScore() { return fraudScore; }
    public void setFraudScore(Integer fraudScore) { this.fraudScore = fraudScore; }
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
}
