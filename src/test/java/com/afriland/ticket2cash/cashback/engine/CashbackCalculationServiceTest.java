package com.afriland.ticket2cash.cashback.engine;

import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.product.CashbackType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashbackCalculationServiceTest {

    private final CashbackCalculationService service = new CashbackCalculationService();

    @Test
    void calculatesFivePercentOfTenThousand() {
        Campaign campaign = activeCampaign(CashbackType.PERCENTAGE, "5");

        CashbackDecision decision = service.calculate(campaign, request("10000"));

        assertTrue(decision.isEligible());
        assertEquals(CashbackDecisionCode.APPROVED, decision.getCode());
        assertEquals(new BigDecimal("500"), decision.getFinalCashback());
    }

    @Test
    void calculatesFixedAmount() {
        Campaign campaign = activeCampaign(CashbackType.FIXED_AMOUNT, "1000");

        CashbackDecision decision = service.calculate(campaign, request("10000"));

        assertTrue(decision.isEligible());
        assertEquals(new BigDecimal("1000"), decision.getFinalCashback());
    }

    @Test
    void rejectsInactiveCampaign() {
        Campaign campaign = activeCampaign(CashbackType.PERCENTAGE, "5");
        campaign.setStatus(CampaignStatus.PAUSED);

        CashbackDecision decision = service.calculate(campaign, request("10000"));

        assertFalse(decision.isEligible());
        assertEquals(CashbackDecisionCode.REJECTED_CAMPAIGN_INACTIVE, decision.getCode());
    }

    @Test
    void rejectsAmountBelowMinimum() {
        Campaign campaign = activeCampaign(CashbackType.PERCENTAGE, "5");
        campaign.setMinTransactionAmount(new BigDecimal("10000"));

        CashbackDecision decision = service.calculate(campaign, request("9999"));

        assertFalse(decision.isEligible());
        assertEquals(CashbackDecisionCode.REJECTED_MIN_AMOUNT_NOT_REACHED, decision.getCode());
    }

    private Campaign activeCampaign(CashbackType type, String value) {
        Campaign campaign = new Campaign();
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCashbackType(type);
        campaign.setCashbackValue(new BigDecimal(value));
        return campaign;
    }

    private CashbackCalculationRequest request(String amount) {
        CashbackCalculationRequest request = new CashbackCalculationRequest();
        request.setTicketAmount(new BigDecimal(amount));
        return request;
    }
}
