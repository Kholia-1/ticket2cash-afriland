package com.afriland.ticket2cash.rewards;

import com.afriland.ticket2cash.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RewardEngineServiceTest {
    @Mock RewardLedgerRepository repository;
    @Mock AuditLogService auditLogService;

    @Test
    void calculatesCashbackPercentAndCreatesLedger() {
        when(repository.findBySourceTypeAndSourceRefAndBenefitType(any(), anyString(), any())).thenReturn(List.of());
        when(repository.save(any(RewardLedgerEntry.class))).thenAnswer(invocation -> {
            RewardLedgerEntry entry = invocation.getArgument(0); entry.setId(1L); return entry;
        });
        RewardCalculationContext context = base(RewardBenefitType.CASHBACK_PERCENT);
        context.setBenefitValue(new BigDecimal("5")); context.setAmount(new BigDecimal("10000"));
        context.setMaskedCard("4578123412349876");

        RewardCalculationResult result = service().calculate(context);

        assertTrue(result.isEligible());
        assertEquals(new BigDecimal("500"), result.getCashbackAmount());
        assertEquals(1L, result.getLedgerEntry().getId());
        assertEquals("****9876", result.getLedgerEntry().getMaskedCard());
        verify(repository).save(any(RewardLedgerEntry.class));
        verify(auditLogService).log(eq("REWARD_LEDGER_CREATED"), eq("REWARDS"), anyString(), isNull(), eq("TX-1"), eq("SUCCESS"), contains("benefitType=CASHBACK_PERCENT"));
    }

    @Test
    void calculatesLoyaltyPointsFromRatePerThousand() {
        when(repository.findBySourceTypeAndSourceRefAndBenefitType(any(), anyString(), any())).thenReturn(List.of());
        when(repository.save(any(RewardLedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RewardCalculationContext context = base(RewardBenefitType.LOYALTY_POINTS);
        context.setAmount(new BigDecimal("25000")); context.setPointsPer1000(10L);

        RewardCalculationResult result = service().calculate(context);

        assertTrue(result.isEligible());
        assertEquals(250L, result.getPointsAmount());
        assertNull(result.getCashbackAmount());
    }

    @Test
    void duplicateSourceAndBenefitIsSkipped() {
        RewardLedgerEntry existing = new RewardLedgerEntry(); existing.setCampaignId(7L);
        when(repository.findBySourceTypeAndSourceRefAndBenefitType(any(), anyString(), any())).thenReturn(List.of(existing));
        RewardCalculationContext context = base(RewardBenefitType.CASHBACK_FIXED); context.setAmount(new BigDecimal("10000")); context.setCampaignId(7L); context.setBenefitValue(new BigDecimal("1000"));

        RewardCalculationResult result = service().calculate(context);

        assertFalse(result.isEligible());
        assertEquals(RewardDecisionCode.REJECTED_DUPLICATE, result.getDecisionCode());
        verify(repository, never()).save(any());
        verify(auditLogService).log(eq("REWARD_DUPLICATE_SKIPPED"), anyString(), anyString(), isNull(), eq("TX-1"), eq("REJECTED"), contains("sourceRef=TX-1"));
    }

    @Test
    void invalidAmountIsRejectedWithoutLedger() {
        RewardCalculationContext context = base(RewardBenefitType.CASHBACK_FIXED); context.setAmount(BigDecimal.ZERO); context.setBenefitValue(new BigDecimal("100"));
        RewardCalculationResult result = service().calculate(context);
        assertEquals(RewardDecisionCode.REJECTED_INVALID_AMOUNT, result.getDecisionCode());
        verify(repository, never()).save(any());
    }

    private RewardEngineService service() { return new RewardEngineService(repository, auditLogService); }
    private RewardCalculationContext base(RewardBenefitType type) {
        RewardCalculationContext c = new RewardCalculationContext(); c.setSourceType(RewardSourceType.CARD_TRANSACTION); c.setSourceRef("TX-1");
        c.setCustomerRef("CUSTOMER-1"); c.setMaskedCard("****1234"); c.setCardHash("HASH_DEMO"); c.setCurrency("FCFA"); c.setBenefitType(type); return c;
    }
}
