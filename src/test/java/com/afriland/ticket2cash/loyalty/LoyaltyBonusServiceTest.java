package com.afriland.ticket2cash.loyalty;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.cashback.engine.CashbackCalculationService;
import com.afriland.ticket2cash.cashback.engine.CashbackDecision;
import com.afriland.ticket2cash.cashback.engine.CashbackTransactionRequest;
import com.afriland.ticket2cash.product.CashbackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyBonusServiceTest {
    @Mock LoyaltyClientRepository clientRepository;
    @Mock LoyaltyTierRepository tierRepository;
    @Mock AuditLogService auditLogService;

    @Test
    void premiumBonusIsAddedToCampaignCashbackWhenEnabled() {
        LoyaltyClient client = new LoyaltyClient();
        client.setAccountNumber("CLIENT-001"); client.setTier("Premium");
        LoyaltyTier tier = tier("Premium", "0.5", true);
        when(clientRepository.findByAccountNumber("CLIENT-001")).thenReturn(Optional.of(client));
        when(tierRepository.findByNameIgnoreCase("Premium")).thenReturn(Optional.of(tier));

        Campaign campaign = campaign(true);
        CashbackDecision decision = calculation().calculateFromTransaction(campaign, request("CLIENT-001", null));

        assertTrue(decision.isEligible());
        assertEquals(new BigDecimal("1250"), decision.getCampaignCashbackAmount());
        assertEquals(new BigDecimal("125"), decision.getLoyaltyBonusAmount());
        assertEquals(new BigDecimal("1375"), decision.getFinalCashback());
        assertEquals("Premium", decision.getLoyaltyTierName());
        verify(auditLogService, atLeastOnce()).log(eq("LOYALTY_BONUS_APPLIED"), anyString(), anyString(), any(), any(), any(), contains("loyaltyBonusAmount=125"));
    }

    @Test
    void cardHashIsUsedWhenCustomerReferenceIsUnavailable() {
        LoyaltyClient client = new LoyaltyClient();
        client.setAccountNumber("CLIENT-002"); client.setCardHash("HASH-002"); client.setTier("Elite");
        when(clientRepository.findByCardHash("HASH-002")).thenReturn(Optional.of(client));
        when(tierRepository.findByNameIgnoreCase("Elite")).thenReturn(Optional.of(tier("Elite", "2", true)));

        CashbackDecision decision = calculation().calculateFromTransaction(campaign(true), request(null, "HASH-002"));

        assertEquals(new BigDecimal("500"), decision.getLoyaltyBonusAmount());
        assertEquals(new BigDecimal("1750"), decision.getFinalCashback());
        verify(clientRepository, never()).findByAccountNumber(anyString());
    }

    @Test
    void importedAccountReferenceInCardHashResolvesPremiumClient() {
        LoyaltyClient client = new LoyaltyClient();
        client.setAccountNumber("HASH-FID-001"); client.setTier("Premium");
        when(clientRepository.findByCardHash("HASH-FID-001")).thenReturn(Optional.empty());
        when(clientRepository.findByAccountNumber("HASH-FID-001")).thenReturn(Optional.of(client));
        when(tierRepository.findByNameIgnoreCase("Premium")).thenReturn(Optional.of(tier("Premium", "0.5", true)));

        CashbackTransactionRequest imported = request(null, "HASH-FID-001");
        imported.setAmount(new BigDecimal("39000"));
        CashbackDecision decision = calculation().calculateFromTransaction(campaign(true), imported);

        assertEquals(new BigDecimal("195"), decision.getLoyaltyBonusAmount());
        assertEquals(new BigDecimal("2145"), decision.getFinalCashback());
        assertEquals("Premium", decision.getLoyaltyTierName());
    }

    @Test
    void staleEssentielTierIsRecomputedFromConfiguredThresholds() {
        LoyaltyClient client = new LoyaltyClient();
        client.setAccountNumber("HASH-FID-002");
        client.setTier("Essentiel");
        client.setLifetimeVolume(new BigDecimal("600000"));
        client.setTransactionCount(1);
        LoyaltyTier essentiel = tier("Essentiel", "0", true);
        essentiel.setMinCumulativeSpend(BigDecimal.ZERO); essentiel.setMinTransactionCount(0); essentiel.setSortOrder(0);
        LoyaltyTier premium = tier("Premium", "0.5", true);
        premium.setMinCumulativeSpend(new BigDecimal("500000")); premium.setMinTransactionCount(10); premium.setSortOrder(1);
        when(clientRepository.findByAccountNumber("HASH-FID-002")).thenReturn(Optional.of(client));
        when(clientRepository.findByCardHash("HASH-FID-002")).thenReturn(Optional.empty());
        when(tierRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(essentiel, premium));
        when(tierRepository.findByNameIgnoreCase("Premium")).thenReturn(Optional.of(premium));

        CashbackTransactionRequest imported = request(null, "HASH-FID-002");
        imported.setAmount(new BigDecimal("39000"));
        CashbackDecision decision = calculation().calculateFromTransaction(campaign(true), imported);

        assertEquals("Premium", decision.getLoyaltyTierName());
        assertEquals(new BigDecimal("195"), decision.getLoyaltyBonusAmount());
        assertEquals(new BigDecimal("2145"), decision.getFinalCashback());
    }

    @Test
    void enabledCampaignWithZeroBonusKeepsCampaignCashbackAsFinalAmount() {
        LoyaltyClient client = new LoyaltyClient();
        client.setAccountNumber("CLIENT-ESS"); client.setTier("Essentiel");
        when(clientRepository.findByAccountNumber("CLIENT-ESS")).thenReturn(Optional.of(client));
        when(tierRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(tierRepository.findByNameIgnoreCase("Essentiel")).thenReturn(Optional.of(tier("Essentiel", "0", true)));

        CashbackDecision decision = calculation().calculateFromTransaction(campaign(true), request("CLIENT-ESS", null));

        assertEquals(new BigDecimal("1250"), decision.getCampaignCashbackAmount());
        assertEquals(BigDecimal.ZERO, decision.getLoyaltyBonusAmount());
        assertEquals(new BigDecimal("1250"), decision.getFinalCashback());
    }

    @Test
    void disabledCampaignKeepsBaseCashbackOnly() {
        CashbackDecision decision = calculation().calculateFromTransaction(campaign(false), request("CLIENT-003", null));
        assertEquals(new BigDecimal("1250"), decision.getFinalCashback());
        assertEquals(BigDecimal.ZERO, decision.getLoyaltyBonusAmount());
        verifyNoInteractions(clientRepository, tierRepository);
    }

    @Test
    void paymentBoundaryMasksAccidentalPanInMaskedCardField() {
        com.afriland.ticket2cash.cashback.CashbackPayment payment = new com.afriland.ticket2cash.cashback.CashbackPayment();
        payment.setMaskedCard("4111111111111234");
        assertEquals("****1234", payment.getMaskedCard());
    }

    private CashbackCalculationService calculation() {
        return new CashbackCalculationService(null, new LoyaltyBonusService(clientRepository, tierRepository, auditLogService), auditLogService);
    }

    private Campaign campaign(boolean enabled) {
        Campaign campaign = new Campaign(); campaign.setId(10L); campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCashbackType(CashbackType.PERCENTAGE); campaign.setCashbackValue(new BigDecimal("5"));
        campaign.setLoyaltyBonusEnabled(enabled); campaign.setStartDate(java.time.LocalDate.now().minusDays(1));
        campaign.setEndDate(java.time.LocalDate.now().plusDays(1)); return campaign;
    }

    private CashbackTransactionRequest request(String customerRef, String cardHash) {
        CashbackTransactionRequest request = new CashbackTransactionRequest(); request.setTransactionRef("TX-LOYALTY");
        request.setCustomerRef(customerRef); request.setCardHash(cardHash == null ? "HASH-DEFAULT" : cardHash);
        request.setMaskedCard("****1234"); request.setAmount(new BigDecimal("25000")); request.setTransactionDateTime(LocalDateTime.now());
        request.setMerchantId(1L); return request;
    }

    private LoyaltyTier tier(String name, String percent, boolean active) {
        LoyaltyTier tier = new LoyaltyTier(); tier.setName(name); tier.setCashbackBonusPercent(new BigDecimal(percent)); tier.setActive(active); return tier;
    }
}
