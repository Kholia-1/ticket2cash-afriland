package com.afriland.ticket2cash.loyalty;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.campaign.Campaign;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
            // Loyalty imports identify the account with the same stable
            // reference that card transactions carry as cardHash.  Support
            // that legacy/import representation without requiring a PAN.
            if (client == null) {
                client = clientRepository.findByAccountNumber(cardHash.trim()).orElse(null);
            }
        }
        if (client == null) {
            return result(campaign, transactionRef, customerRef, maskedCard, null, null,
                    LoyaltyBonusDecisionCode.CLIENT_NOT_FOUND, "Client fidélité introuvable");
        }
        String effectiveTierName = resolveEffectiveTier(client);
        if (effectiveTierName == null || effectiveTierName.isBlank()) {
            return result(campaign, transactionRef, customerRef, maskedCard, null, null,
                    LoyaltyBonusDecisionCode.CLIENT_WITHOUT_TIER, "Client sans niveau fidélité");
        }
        LoyaltyTier tier = tierRepository.findByNameIgnoreCase(effectiveTierName.trim()).orElse(null);
        if (tier == null) {
            return result(campaign, transactionRef, customerRef, maskedCard, effectiveTierName, null,
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

    /**
     * Resolve the current tier from the configured cumulative-volume bands.
     * Transaction-count and minimum-transaction rules are evaluated by the
     * advantage calculator; they must not leave an imported high-volume client
     * stuck on the legacy Essentiel tier.
     */
    private String resolveEffectiveTier(LoyaltyClient client) {
        List<LoyaltyTier> tiers = tierRepository.findByActiveTrueOrderBySortOrderAsc();
        if (tiers != null && !tiers.isEmpty()) {
            BigDecimal volume = client.getLifetimeVolume() == null ? BigDecimal.ZERO : client.getLifetimeVolume();
            String selected = null;
            for (LoyaltyTier candidate : tiers) {
                BigDecimal minimumVolume = candidate.getMinCumulativeSpend() == null
                        ? BigDecimal.ZERO : candidate.getMinCumulativeSpend();
                if (volume.compareTo(minimumVolume) >= 0) {
                    selected = candidate.getName();
                }
            }
            if (selected != null && !selected.isBlank()) return selected;
        }
        if (client.getTier() == null || client.getTier().isBlank()
                || "CLASSIC".equalsIgnoreCase(client.getTier())) return "Essentiel";
        return client.getTier().trim();
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
