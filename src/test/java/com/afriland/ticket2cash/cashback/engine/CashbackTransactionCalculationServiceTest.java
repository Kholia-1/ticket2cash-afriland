package com.afriland.ticket2cash.cashback.engine;

import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.campaign.MccCode;
import com.afriland.ticket2cash.product.CashbackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CashbackTransactionCalculationServiceTest {

    @Mock
    private com.afriland.ticket2cash.cashback.CashbackPaymentRepository paymentRepository;

    @Test
    void transactionOfTwentyFiveThousandGetsFivePercent() {
        Campaign campaign = campaign();
        CashbackTransactionRequest request = request("TX-001", "25000");

        CashbackDecision decision = new CashbackCalculationService().calculateFromTransaction(campaign, request);

        assertTrue(decision.isEligible());
        assertEquals(new BigDecimal("1250"), decision.getFinalCashback());
    }

    @Test
    void transactionAlreadyProcessedIsRejected() {
        CashbackTransactionRequest request = request("TX-002", "25000");
        com.afriland.ticket2cash.cashback.CashbackPayment existing = new com.afriland.ticket2cash.cashback.CashbackPayment();
        existing.setTransactionRef("TX-002");
        when(paymentRepository.findByTransactionRef("TX-002")).thenReturn(Optional.of(existing));

        CashbackDecision decision = new CashbackCalculationService(paymentRepository)
                .calculateFromTransaction(campaign(), request);

        assertFalse(decision.isEligible());
        assertEquals(CashbackDecisionCode.REJECTED_DUPLICATE_TICKET, decision.getCode());
    }

    @Test
    void transactionOutsideCampaignPeriodIsRejected() {
        Campaign campaign = campaign();
        campaign.setStartDate(java.time.LocalDate.now().plusDays(1));

        CashbackDecision decision = new CashbackCalculationService().calculateFromTransaction(campaign, request("TX-003", "25000"));

        assertFalse(decision.isEligible());
        assertEquals(CashbackDecisionCode.REJECTED_CAMPAIGN_OUT_OF_PERIOD, decision.getCode());
    }

    @Test
    void transactionBelowMinimumIsRejected() {
        Campaign campaign = campaign();
        campaign.setMinTransactionAmount(new BigDecimal("30000"));

        CashbackDecision decision = new CashbackCalculationService().calculateFromTransaction(campaign, request("TX-004", "25000"));

        assertFalse(decision.isEligible());
        assertEquals(CashbackDecisionCode.REJECTED_MIN_AMOUNT_NOT_REACHED, decision.getCode());
    }

    @Test
    void transactionWithDifferentMccIsRejected() {
        Campaign campaign = campaign();
        campaign.setMccCode(MccCode.MCC_5411);
        CashbackTransactionRequest request = request("TX-005", "25000");
        request.setMccCode("5812");

        CashbackDecision decision = new CashbackCalculationService().calculateFromTransaction(campaign, request);

        assertFalse(decision.isEligible());
        assertEquals(CashbackDecisionCode.REJECTED_MCC_NOT_ELIGIBLE, decision.getCode());
    }

    private Campaign campaign() {
        Campaign campaign = new Campaign();
        campaign.setId(10L);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCashbackType(CashbackType.PERCENTAGE);
        campaign.setCashbackValue(new BigDecimal("5"));
        campaign.setStartDate(java.time.LocalDate.now().minusDays(1));
        campaign.setEndDate(java.time.LocalDate.now().plusDays(1));
        return campaign;
    }

    private CashbackTransactionRequest request(String ref, String amount) {
        CashbackTransactionRequest request = new CashbackTransactionRequest();
        request.setTransactionRef(ref);
        request.setCardHash("card-hash");
        request.setAmount(new BigDecimal(amount));
        request.setTransactionDateTime(LocalDateTime.now());
        request.setMerchantId(1L);
        return request;
    }
}
