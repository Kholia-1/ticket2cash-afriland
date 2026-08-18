package com.afriland.ticket2cash.rewards;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardSummaryControllerTest {
    @Mock RewardLedgerRepository repository;

    @Test
    void summaryIsStableWhenLedgerIsEmpty() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findTop100ByOrderByCalculatedAtDesc()).thenReturn(List.of());

        Map<String, Object> result = new RewardSummaryController(repository).summary();

        assertEquals(BigDecimal.ZERO, result.get("cashbackCalculated"));
        assertEquals(0L, result.get("loyaltyPointsGenerated"));
        assertEquals(List.of(), result.get("latestEntries"));
    }

    @Test
    void summaryAndLedgerExposeBenefitsWithoutFullPan() {
        RewardLedgerEntry entry = new RewardLedgerEntry();
        entry.setId(3L); entry.setSourceType(RewardSourceType.CARD_TRANSACTION); entry.setSourceRef("TX-3");
        entry.setCustomerRef("CUSTOMER-3"); entry.setMaskedCard("4578123412349876");
        entry.setBenefitType(RewardBenefitType.CASHBACK_PERCENT); entry.setCashbackAmount(new BigDecimal("500"));
        entry.setDecisionCode(RewardDecisionCode.APPROVED); entry.setStatus("CALCULATED");
        when(repository.findAll()).thenReturn(List.of(entry));
        when(repository.findTop100ByOrderByCalculatedAtDesc()).thenReturn(List.of(entry));

        Map<String, Object> summary = new RewardSummaryController(repository).summary();
        Map<String, Object> row = new RewardSummaryController(repository).ledger().get(0);

        assertEquals(new BigDecimal("500"), summary.get("cashbackCalculated"));
        assertEquals("****9876", row.get("maskedCard"));
        assertFalse(String.valueOf(row.get("maskedCard")).matches("\\d{13,19}"));
    }
}
