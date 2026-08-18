package com.afriland.ticket2cash.loyalty;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.campaign.Campaign;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Resolves an optional loyalty-tier bonus without ever requiring a PAN. */
@Service
public class LoyaltyBonusService {
    private final LoyaltyClientRepository clientRepository;
    private final LoyaltyTierRepository tierRepository;
    private final AuditLogService auditLogService;

    public LoyaltyBonusService(LoyaltyClientRepository clientRepository,
                               LoyaltyTierRepository tierRepository,
                               AuditLogService auditLogService) {
        this.clientRepository = clientRepository;
        this.tierRepository = tierRepository;
        this.auditLogService = auditLogService;
    }

    public LoyaltyBonusResult resolve(Campaign campaign, String customerRef, String cardHash,
                                      BigDecimal transactionAmount, String transactionRef,
                                      String maskedCard) {
        if (!Boolean.TRUE.equals(campaign != null ? campaign.getLoyaltyBonusEnabled() : false)) {
            return result(campaign, transactionRef, customerRef, maskedCard, null, null,
                    LoyaltyBonusDecisionCode.NOT_ENABLED, "Bonus fidélité non autorisé par la campagne");
        }
        LoyaltyClient client = null;
        if (customerRef != null && !customerRef.isBlank()) {
            client = clientRepository.findByAccountNumber(customerRef.trim()).orElse(null);
        }
        if (client == null && cardHash != null && !cardHash.isBlank()) {
            client = clientRepository.findByCardHash(cardHash.trim()).orElse(null);
        }
        if (client == null) {
            return result(campaign, transactionRef, customerRef, maskedCard, null, null,
                    LoyaltyBonusDecisionCode.CLIENT_NOT_FOUND, "Client fidélité introuvable");
        }
        if (client.getTier() == null || client.getTier().isBlank()) {
            return result(campaign, transactionRef, customerRef, maskedCard, null, null,
                    LoyaltyBonusDecisionCode.CLIENT_WITHOUT_TIER, "Client sans niveau fidélité");
        }
        LoyaltyTier tier = tierRepository.findByNameIgnoreCase(client.getTier().trim()).orElse(null);
        if (tier == null) {
            return result(campaign, transactionRef, customerRef, maskedCard, client.getTier(), null,
                    LoyaltyBonusDecisionCode.TIER_NOT_FOUND, "Niveau fidélité introuvable");
        }
        if (!Boolean.TRUE.equals(tier.getActive())) {
            return result(campaign, transactionRef, customerRef, maskedCard, tier.getName(), null,
                    LoyaltyBonusDecisionCode.TIER_INACTIVE, "Niveau fidélité inactif");
        }
        BigDecimal percent = tier.getCashbackBonusPercent();
        if (percent == null || percent.signum() <= 0 || transactionAmount == null || transactionAmount.signum() <= 0) {
            return result(campaign, transactionRef, customerRef, maskedCard, tier.getName(), BigDecimal.ZERO,
                    LoyaltyBonusDecisionCode.NO_BONUS, "Aucun bonus fidélité applicable");
        }
        BigDecimal amount = transactionAmount.multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return result(campaign, transactionRef, customerRef, maskedCard, tier.getName(), amount,
                LoyaltyBonusDecisionCode.APPLIED, "Bonus fidélité appliqué").withPercent(percent);
    }

    private LoyaltyBonusResult result(Campaign campaign, String transactionRef, String customerRef,
                                      String maskedCard, String tierName, BigDecimal amount,
                                      LoyaltyBonusDecisionCode code, String reason) {
        LoyaltyBonusResult result = LoyaltyBonusResult.of(tierName, BigDecimal.ZERO,
                amount == null ? BigDecimal.ZERO : amount, code, reason);
        String action = code == LoyaltyBonusDecisionCode.APPLIED ? "LOYALTY_BONUS_RESOLVED" : "LOYALTY_BONUS_NOT_APPLIED";
        try {
            auditLogService.log(action, "LOYALTY", "LoyaltyBonus", campaign == null ? null : campaign.getId(),
                    customerRef, code == LoyaltyBonusDecisionCode.APPLIED ? "SUCCESS" : "INFO",
                    "transactionRef=" + safe(transactionRef) + " | customerRef=" + safe(customerRef)
                            + " | maskedCard=" + safe(maskedCard)
                            + " | campaignId=" + (campaign == null ? "" : campaign.getId())
                            + " | campaignName=" + safe(campaign == null ? null : campaign.getName())
                            + " | loyaltyBonusEnabled=" + (campaign != null && Boolean.TRUE.equals(campaign.getLoyaltyBonusEnabled()))
                            + " | tierName=" + safe(tierName) + " | decisionCode=" + code
                            + " | reason=" + safe(reason));
        } catch (RuntimeException ignored) { }
        return result;
    }

    private String safe(String value) { return value == null ? "" : value.replaceAll("[\\r\\n]", " "); }
}
