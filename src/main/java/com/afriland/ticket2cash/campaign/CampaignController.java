package com.afriland.ticket2cash.campaign;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.common.ValidationUtils;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.product.CashbackType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Campaign management for the unified "moteur d'animation de campagnes".
 * See {@code Architecture_Moteur_Campagnes.md} for the full design.
 *
 * <p><b>Role scoping:</b>
 * <ul>
 *   <li><b>ADMIN</b> creates campaigns on any merchant. Owns them as ADMIN.</li>
 *   <li><b>PARTNER (merchant)</b> creates campaigns only for their own merchant.
 *       Owns them as MERCHANT. The merchantId in the request body is ignored —
 *       always forced to the partner's own session merchantId.</li>
 *   <li><b>OPERATEUR / LECTEUR</b> read-only.</li>
 * </ul>
 *
 * <p><b>Trigger types:</b> each campaign has a {@link CampaignTriggerType} that
 * determines what data source will trigger cashback computation. Different
 * trigger types require different fields — the controller validates that
 * trigger-specific fields are consistent with the chosen trigger.
 */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignRepository campaignRepository;
    private final MerchantRepository merchantRepository;
    private final AuditLogService auditLogService;

    public CampaignController(CampaignRepository campaignRepository,
                              MerchantRepository merchantRepository,
                              AuditLogService auditLogService) {
        this.campaignRepository = campaignRepository;
        this.merchantRepository = merchantRepository;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------- helpers

    private String currentRole(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        return s == null ? null : String.valueOf(s.getAttribute("AUTH_ROLE"));
    }

    private Long currentMerchantId(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        if (s == null) return null;
        Object mid = s.getAttribute("AUTH_MERCHANT_ID");
        if (mid == null) return null;
        try { return Long.valueOf(String.valueOf(mid)); }
        catch (NumberFormatException e) { return null; }
    }

    private String currentActor(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        if (s == null) return "ANONYMOUS";
        Object u = s.getAttribute("AUTH_USERNAME");
        return u == null ? "ANONYMOUS" : String.valueOf(u);
    }

    private boolean isAdmin(HttpServletRequest http) {
        return "ADMIN".equalsIgnoreCase(currentRole(http));
    }

    private boolean isPartner(HttpServletRequest http) {
        return "PARTNER".equalsIgnoreCase(currentRole(http));
    }

    /**
     * Only ADMIN can create/edit campaigns. Partners must send a request
     * via {@code /api/partner-requests} with type CAMPAIGN_REQUEST, and admin
     * creates the campaign after review.
     */
    private boolean canWrite(HttpServletRequest http) {
        return isAdmin(http);
    }

    private boolean isMine(Campaign c, HttpServletRequest http) {
        // Admin owns visibility to all campaigns
        if (isAdmin(http)) return true;
        // Partner may still be a legitimate viewer of campaigns targeting their merchant
        Long me = currentMerchantId(http);
        if (me == null) return false;
        Merchant m = c.getMerchant();
        return m != null && m.getId() != null && m.getId().equals(me);
    }

    /**
     * Validates trigger-specific fields are consistent with the chosen trigger.
     * Returns null when valid, or a human-readable error message otherwise.
     */
    private String validateTriggerSpecific(CampaignTriggerType trigger, CampaignRequest req) {
        if (trigger == null) return null;
        switch (trigger) {
            case PRODUCT_PURCHASE:
                if (req.getTargetProductSkus() == null || req.getTargetProductSkus().trim().isEmpty()) {
                    return "Pour un cashback produit, précisez au moins un SKU cible";
                }
                break;
            case VOLUME_THRESHOLD:
                if (req.getVolumeThreshold() == null || req.getVolumeThreshold().signum() <= 0) {
                    return "Pour un cashback volume, précisez un seuil de volume strictement positif";
                }
                break;
            case POS_WEBHOOK_EVENT:
                if (req.getAmountThreshold() == null || req.getAmountThreshold().signum() <= 0) {
                    return "Pour un cashback événement POS, précisez un seuil par transaction strictement positif";
                }
                if (req.getCashbackType() == CashbackType.PERCENTAGE) {
                    return "Le cashback événement POS doit être un montant fixe, pas un pourcentage";
                }
                break;
            case MERCHANT_TRANSACTION:
                break;
        }
        return null;
    }

    // ---------------------------------------------------------------- READ

    @GetMapping
    public List<Campaign> getAllCampaigns(HttpServletRequest http) {
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null) return Collections.emptyList();
            return campaignRepository.findByMerchantId(me);
        }
        return campaignRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCampaignById(@PathVariable Long id, HttpServletRequest http) {
        return campaignRepository.findById(id)
                .map(c -> {
                    if (isPartner(http) && !isMine(c, http)) {
                        return ResponseEntity.status(403).body((Object) "Not your campaign");
                    }
                    return ResponseEntity.ok((Object) c);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<?> getCampaignsByMerchant(@PathVariable Long merchantId,
                                                     HttpServletRequest http) {
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null || !me.equals(merchantId)) {
                return ResponseEntity.status(403).body("Not your merchant");
            }
        }
        return ResponseEntity.ok(campaignRepository.findByMerchantId(merchantId));
    }

    @GetMapping("/status/{status}")
    public List<Campaign> getCampaignsByStatus(@PathVariable CampaignStatus status,
                                                HttpServletRequest http) {
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null) return Collections.emptyList();
            return campaignRepository.findByMerchantId(me).stream()
                    .filter(c -> c.getStatus() == status)
                    .toList();
        }
        return campaignRepository.findByStatus(status);
    }

    /** New: filter campaigns by trigger type — used by the admin "Campagnes par type" view. */
    @GetMapping("/trigger/{triggerType}")
    public List<Campaign> getCampaignsByTrigger(@PathVariable CampaignTriggerType triggerType,
                                                 HttpServletRequest http) {
        List<Campaign> all;
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null) return Collections.emptyList();
            all = campaignRepository.findByMerchantId(me);
        } else {
            all = campaignRepository.findAll();
        }
        return all.stream().filter(c -> c.getTriggerType() == triggerType).toList();
    }

    // ---------------------------------------------------------------- CREATE

    @PostMapping
    public ResponseEntity<?> createCampaign(@RequestBody CampaignRequest request,
                                             HttpServletRequest http) {
        if (!canWrite(http)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "Seul l'administrateur Afriland peut créer des campagnes. Les partenaires peuvent soumettre une demande via l'onglet Demandes."));
        }

        // ADMIN always chooses the target merchant
        Long targetMerchantId = request.getMerchantId();
        if (targetMerchantId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Précisez le commerçant cible",
                    "field", "merchantId"));
        }
        Merchant merchant = merchantRepository.findById(targetMerchantId).orElse(null);
        if (merchant == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Commerçant introuvable"));
        }
        CampaignOwnerType ownerType = CampaignOwnerType.ADMIN;

        // Validate common fields
        String err = ValidationUtils.validateName(request.getName(), "nom de la campagne");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));

        err = ValidationUtils.validateDescription(request.getDescription(), "description", 500);
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "description"));

        err = ValidationUtils.validateDateRange(request.getStartDate(), request.getEndDate());
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "endDate"));

        if (request.getCashbackValue() != null) {
            CashbackType ct = request.getCashbackType() != null ? request.getCashbackType() : CashbackType.NONE;
            err = (ct == CashbackType.PERCENTAGE)
                    ? ValidationUtils.validatePercentage(request.getCashbackValue(), "pourcentage cashback")
                    : ValidationUtils.validatePositiveOrZero(request.getCashbackValue(), "montant cashback");
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "cashbackValue"));
        }

        err = ValidationUtils.validatePositive(request.getTotalBudget(), "budget total");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "totalBudget"));

        CampaignTriggerType trigger = request.getTriggerType() != null
                ? request.getTriggerType() : CampaignTriggerType.MERCHANT_TRANSACTION;
        String triggerErr = validateTriggerSpecific(trigger, request);
        if (triggerErr != null) {
            return ResponseEntity.badRequest().body(Map.of("error", triggerErr, "field", "triggerType"));
        }

        // Build entity
        Campaign campaign = new Campaign();
        campaign.setOwnerType(ownerType);
        campaign.setMerchant(merchant);
        campaign.setTriggerType(trigger);
        campaign.setName(request.getName().trim());
        campaign.setDescription(request.getDescription());
        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getEndDate());
        campaign.setStatus(request.getStatus() != null ? request.getStatus() : CampaignStatus.DRAFT);

        campaign.setEntityTypeFilter(request.getEntityTypeFilter());
        campaign.setTierFilter(request.getTierFilter());
        campaign.setMinTransactionAmount(request.getMinTransactionAmount());
        campaign.setMaxCashbackPerClient(request.getMaxCashbackPerClient());

        campaign.setCashbackType(request.getCashbackType() != null ? request.getCashbackType() : CashbackType.NONE);
        campaign.setCashbackValue(request.getCashbackValue() != null ? request.getCashbackValue() : BigDecimal.ZERO);
        campaign.setDailyLimitPerClient(request.getDailyLimitPerClient());
        campaign.setMonthlyLimitPerClient(request.getMonthlyLimitPerClient());
        campaign.setTotalBudget(request.getTotalBudget());

        campaign.setTargetProductSkus(request.getTargetProductSkus());
        campaign.setVolumeThreshold(request.getVolumeThreshold());
        campaign.setAmountThreshold(request.getAmountThreshold());
        campaign.setCategoryFilter(request.getCategoryFilter());

        // GL-01 filters
        campaign.setMccCode(request.getMccCode());
        campaign.setChannelFilter(request.getChannelFilter());
        campaign.setCorridorFilter(request.getCorridorFilter());
        campaign.setCardBinStart(request.getCardBinStart());
        campaign.setCardBinEnd(request.getCardBinEnd());

        campaign.setCreatedBy(currentActor(http));

        Campaign saved = campaignRepository.save(campaign);

        auditLogService.log("CREATE_CAMPAIGN", "CAMPAIGN", "Campaign", saved.getId(),
                currentActor(http), "SUCCESS",
                String.format("Campaign %s [%s] created on merchant %s",
                        saved.getName(), trigger, merchant.getName()));

        return ResponseEntity.ok(saved);
    }

    // ---------------------------------------------------------------- UPDATE

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCampaign(@PathVariable Long id,
                                             @RequestBody CampaignRequest patch,
                                             HttpServletRequest http) {
        if (!canWrite(http)) {
            return ResponseEntity.status(403).body(Map.of("error", "Non autorisé"));
        }

        Campaign existing = campaignRepository.findById(id).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        if (isPartner(http) && !isMine(existing, http)) {
            return ResponseEntity.status(403).body(Map.of("error", "Ce n'est pas votre campagne"));
        }

        if (patch.getName() != null) {
            String err = ValidationUtils.validateName(patch.getName(), "nom de la campagne");
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));
            existing.setName(patch.getName().trim());
        }
        if (patch.getDescription() != null) {
            String err = ValidationUtils.validateDescription(patch.getDescription(), "description", 500);
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "description"));
            existing.setDescription(patch.getDescription());
        }
        if (patch.getStartDate() != null) existing.setStartDate(patch.getStartDate());
        if (patch.getEndDate() != null) existing.setEndDate(patch.getEndDate());
        String dateErr = ValidationUtils.validateDateRange(existing.getStartDate(), existing.getEndDate());
        if (dateErr != null) return ResponseEntity.badRequest().body(Map.of("error", dateErr, "field", "endDate"));

        if (patch.getStatus() != null) existing.setStatus(patch.getStatus());
        if (patch.getTriggerType() != null) existing.setTriggerType(patch.getTriggerType());

        if (patch.getCashbackType() != null) existing.setCashbackType(patch.getCashbackType());
        if (patch.getCashbackValue() != null) {
            CashbackType ct = existing.getCashbackType() != null ? existing.getCashbackType() : CashbackType.NONE;
            String err = (ct == CashbackType.PERCENTAGE)
                    ? ValidationUtils.validatePercentage(patch.getCashbackValue(), "pourcentage cashback")
                    : ValidationUtils.validatePositiveOrZero(patch.getCashbackValue(), "montant cashback");
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "cashbackValue"));
            existing.setCashbackValue(patch.getCashbackValue());
        }
        if (patch.getTotalBudget() != null) {
            String err = ValidationUtils.validatePositive(patch.getTotalBudget(), "budget total");
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "totalBudget"));
            existing.setTotalBudget(patch.getTotalBudget());
        }

        if (patch.getEntityTypeFilter() != null) existing.setEntityTypeFilter(patch.getEntityTypeFilter());
        if (patch.getTierFilter() != null) existing.setTierFilter(patch.getTierFilter());
        if (patch.getMinTransactionAmount() != null) existing.setMinTransactionAmount(patch.getMinTransactionAmount());
        if (patch.getMaxCashbackPerClient() != null) existing.setMaxCashbackPerClient(patch.getMaxCashbackPerClient());
        if (patch.getDailyLimitPerClient() != null) existing.setDailyLimitPerClient(patch.getDailyLimitPerClient());
        if (patch.getMonthlyLimitPerClient() != null) existing.setMonthlyLimitPerClient(patch.getMonthlyLimitPerClient());

        if (patch.getTargetProductSkus() != null) existing.setTargetProductSkus(patch.getTargetProductSkus());
        if (patch.getVolumeThreshold() != null) existing.setVolumeThreshold(patch.getVolumeThreshold());
        if (patch.getAmountThreshold() != null) existing.setAmountThreshold(patch.getAmountThreshold());
        if (patch.getCategoryFilter() != null) existing.setCategoryFilter(patch.getCategoryFilter());

        // GL-01 filters
        if (patch.getMccCode() != null) existing.setMccCode(patch.getMccCode());
        if (patch.getChannelFilter() != null) existing.setChannelFilter(patch.getChannelFilter());
        if (patch.getCorridorFilter() != null) existing.setCorridorFilter(patch.getCorridorFilter());
        if (patch.getCardBinStart() != null) existing.setCardBinStart(patch.getCardBinStart());
        if (patch.getCardBinEnd() != null) existing.setCardBinEnd(patch.getCardBinEnd());

        Campaign updated = campaignRepository.save(existing);
        auditLogService.log("UPDATE_CAMPAIGN", "CAMPAIGN", "Campaign",
                updated.getId(), currentActor(http), "SUCCESS",
                "Campaign updated: " + updated.getName());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateCampaignStatus(@PathVariable Long id,
                                                   @RequestParam CampaignStatus status,
                                                   HttpServletRequest http) {
        if (!canWrite(http)) return ResponseEntity.status(403).body("Non autorisé");
        return campaignRepository.findById(id)
                .map(campaign -> {
                    if (isPartner(http) && !isMine(campaign, http)) {
                        return ResponseEntity.status(403).body((Object) "Ce n'est pas votre campagne");
                    }
                    campaign.setStatus(status);
                    Campaign updated = campaignRepository.save(campaign);
                    auditLogService.log("UPDATE_CAMPAIGN_STATUS", "CAMPAIGN", "Campaign",
                            updated.getId(), currentActor(http), "SUCCESS",
                            "Campaign status → " + status);
                    return ResponseEntity.ok((Object) updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------- DELETE

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCampaign(@PathVariable Long id, HttpServletRequest http) {
        if (!canWrite(http)) return ResponseEntity.status(403).body("Non autorisé");
        return campaignRepository.findById(id).map(campaign -> {
            if (isPartner(http) && !isMine(campaign, http)) {
                return ResponseEntity.status(403).body((Object) "Ce n'est pas votre campagne");
            }
            campaignRepository.deleteById(id);
            auditLogService.log("DELETE_CAMPAIGN", "CAMPAIGN", "Campaign", id,
                    currentActor(http), "SUCCESS",
                    "Campaign deleted: " + campaign.getName());
            return ResponseEntity.ok((Object) Map.of("message", "Campaign deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
