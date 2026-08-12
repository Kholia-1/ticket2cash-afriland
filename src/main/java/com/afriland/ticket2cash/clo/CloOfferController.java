package com.afriland.ticket2cash.clo;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.card.PrepaidCard;
import com.afriland.ticket2cash.card.PrepaidCardRepository;
import com.afriland.ticket2cash.common.ValidationUtils;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Card-Linked Offers controller (GL-04). Manages offers, opt-ins, and
 * redemptions for the CLO programme built on Afriland prepaid cards.
 *
 * <h3>Scoping</h3>
 * <ul>
 *   <li>ADMIN — full CRUD on offers, opt-in management, redemption simulation</li>
 *   <li>PARTNER — sees offers they sponsor (their merchant), read-only</li>
 *   <li>Cardholder — opt-in/opt-out via public endpoint (future Flutter app)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/clo")
public class CloOfferController {

    private final CloOfferRepository offerRepo;
    private final CloOptInRepository optInRepo;
    private final CloRedemptionRepository redemptionRepo;
    private final PrepaidCardRepository cardRepo;
    private final MerchantRepository merchantRepo;
    private final AuditLogService auditLogService;

    public CloOfferController(CloOfferRepository offerRepo,
                              CloOptInRepository optInRepo,
                              CloRedemptionRepository redemptionRepo,
                              PrepaidCardRepository cardRepo,
                              MerchantRepository merchantRepo,
                              AuditLogService auditLogService) {
        this.offerRepo = offerRepo;
        this.optInRepo = optInRepo;
        this.redemptionRepo = redemptionRepo;
        this.cardRepo = cardRepo;
        this.merchantRepo = merchantRepo;
        this.auditLogService = auditLogService;
    }

    // -------------------------------------------------------------- Auth

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
    private String currentUser(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        if (s == null) return "ANONYMOUS";
        Object u = s.getAttribute("AUTH_USERNAME");
        return u == null ? "ANONYMOUS" : String.valueOf(u);
    }
    private boolean isAdmin(HttpServletRequest http) {
        return "ADMIN".equalsIgnoreCase(currentRole(http));
    }

    // ============================================================== OFFERS

    @GetMapping("/offers")
    public List<CloOffer> listOffers(HttpServletRequest http) {
        if ("PARTNER".equalsIgnoreCase(currentRole(http))) {
            Long me = currentMerchantId(http);
            if (me == null) return java.util.Collections.emptyList();
            return offerRepo.findByMerchantIdOrderByCreatedAtDesc(me);
        }
        return offerRepo.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/offers/{id}")
    public ResponseEntity<CloOffer> getOffer(@PathVariable Long id) {
        return offerRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Offers eligible for a specific cardholder (opted-in + BIN match). */
    @GetMapping("/offers/available/{accountNumber}")
    public List<CloOffer> availableFor(@PathVariable String accountNumber) {
        // Must be opted-in
        boolean optedIn = optInRepo.findByAccountNumber(accountNumber)
                .map(CloOptIn::getOptedIn).orElse(false);
        if (!optedIn) return java.util.Collections.emptyList();

        // What BINs does this customer own?
        List<PrepaidCard> cards = cardRepo.findByOwnerAccountNumberOrderByCreatedAtDesc(accountNumber);
        if (cards.isEmpty()) return java.util.Collections.emptyList();
        java.util.Set<String> customerBins = new java.util.HashSet<>();
        for (PrepaidCard c : cards) customerBins.add(c.getBin());

        // Filter active offers
        return offerRepo.findByStatusOrderByCreatedAtDesc(CloOfferStatus.ACTIVE).stream()
                .filter(o -> {
                    if (o.getTargetBins() == null || o.getTargetBins().isEmpty()) return true;
                    for (String bin : o.getTargetBins().split(",")) {
                        if (customerBins.contains(bin.trim())) return true;
                    }
                    return false;
                }).collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/offers")
    public ResponseEntity<?> createOffer(@RequestBody CloOffer body, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        String err = ValidationUtils.validateName(body.getName(), "nom de l'offre");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));

        if (body.getMerchant() == null || body.getMerchant().getId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Merchant sponsor requis", "field", "merchantId"));
        }
        Merchant m = merchantRepo.findById(body.getMerchant().getId()).orElse(null);
        if (m == null) return ResponseEntity.badRequest().body(Map.of("error", "Marchand introuvable"));
        body.setMerchant(m);

        if (body.getRewardType() == null) return ResponseEntity.badRequest().body(Map.of("error", "Type de récompense requis", "field", "rewardType"));
        err = ValidationUtils.validatePositive(body.getRewardValue(), "valeur de récompense");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "rewardValue"));

        if (body.getRewardType() == CloRewardType.CASHBACK_PERCENT) {
            err = ValidationUtils.validatePercentage(body.getRewardValue(), "pourcentage");
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "rewardValue"));
        }
        err = ValidationUtils.validateDateRange(body.getValidFrom(), body.getValidTo());
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "validTo"));

        body.setId(null);
        body.setName(body.getName().trim());
        body.setCreatedBy(currentUser(http));
        body.setBudgetUsed(BigDecimal.ZERO);
        body.setRedemptionCount(0L);
        if (body.getStatus() == null) body.setStatus(CloOfferStatus.DRAFT);

        CloOffer saved = offerRepo.save(body);
        auditLogService.log("CREATE_CLO_OFFER", "CLO", "CloOffer", saved.getId(),
                currentUser(http), "SUCCESS", "Offer created: " + saved.getName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/offers/{id}")
    public ResponseEntity<?> updateOffer(@PathVariable Long id, @RequestBody CloOffer patch, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return offerRepo.findById(id).map(existing -> {
            if (patch.getName() != null) existing.setName(patch.getName().trim());
            if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
            if (patch.getRewardType() != null) existing.setRewardType(patch.getRewardType());
            if (patch.getRewardValue() != null) existing.setRewardValue(patch.getRewardValue());
            if (patch.getTargetBins() != null) existing.setTargetBins(patch.getTargetBins());
            if (patch.getMinTransactionAmount() != null) existing.setMinTransactionAmount(patch.getMinTransactionAmount());
            if (patch.getMaxRewardPerCardholder() != null) existing.setMaxRewardPerCardholder(patch.getMaxRewardPerCardholder());
            if (patch.getTotalBudget() != null) existing.setTotalBudget(patch.getTotalBudget());
            if (patch.getChannelFilter() != null) existing.setChannelFilter(patch.getChannelFilter());
            if (patch.getValidFrom() != null) existing.setValidFrom(patch.getValidFrom());
            if (patch.getValidTo() != null) existing.setValidTo(patch.getValidTo());
            if (patch.getStatus() != null) existing.setStatus(patch.getStatus());
            CloOffer saved = offerRepo.save(existing);
            auditLogService.log("UPDATE_CLO_OFFER", "CLO", "CloOffer", saved.getId(),
                    currentUser(http), "SUCCESS", "Offer updated: " + saved.getName());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/offers/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return offerRepo.findById(id).map(o -> {
            o.setStatus(CloOfferStatus.ACTIVE);
            offerRepo.save(o);
            auditLogService.log("ACTIVATE_CLO_OFFER", "CLO", "CloOffer", id,
                    currentUser(http), "SUCCESS", "Offer activated: " + o.getName());
            return ResponseEntity.ok((Object) o);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/offers/{id}/pause")
    public ResponseEntity<?> pause(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return offerRepo.findById(id).map(o -> {
            o.setStatus(CloOfferStatus.PAUSED);
            offerRepo.save(o);
            return ResponseEntity.ok((Object) o);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/offers/{id}")
    public ResponseEntity<?> deleteOffer(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return offerRepo.findById(id).map(o -> {
            o.setStatus(CloOfferStatus.ARCHIVED);
            offerRepo.save(o);
            auditLogService.log("ARCHIVE_CLO_OFFER", "CLO", "CloOffer", id,
                    currentUser(http), "SUCCESS", "Offer archived");
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // ============================================================== OPT-IN

    @GetMapping("/opt-ins")
    public List<CloOptIn> listOptIns() { return optInRepo.findByOptedInTrueOrderByOptedInAtDesc(); }

    @GetMapping("/opt-ins/{accountNumber}")
    public ResponseEntity<CloOptIn> getOptIn(@PathVariable String accountNumber) {
        return optInRepo.findByAccountNumber(accountNumber)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Set opt-in state for a cardholder. Admin or the cardholder themselves.
     * Body: {optedIn (bool), ownerName?, notificationChannels?}
     */
    @PostMapping("/opt-ins/{accountNumber}")
    public ResponseEntity<?> setOptIn(@PathVariable String accountNumber,
                                       @RequestBody Map<String, Object> body,
                                       HttpServletRequest http) {
        boolean optIn = Boolean.parseBoolean(String.valueOf(body.getOrDefault("optedIn", "true")));
        String ownerName = body.get("ownerName") != null ? String.valueOf(body.get("ownerName")) : null;
        String channels = body.get("notificationChannels") != null
                ? String.valueOf(body.get("notificationChannels")) : "PUSH,SMS";

        CloOptIn record = optInRepo.findByAccountNumber(accountNumber).orElse(null);
        if (record == null) {
            record = new CloOptIn();
            record.setAccountNumber(accountNumber);
        }
        record.setOptedIn(optIn);
        if (ownerName != null && !ownerName.isEmpty()) record.setOwnerName(ownerName);
        record.setNotificationChannels(channels);
        if (optIn) {
            record.setOptedInAt(LocalDateTime.now());
            record.setRevokedAt(null);
        } else {
            record.setRevokedAt(LocalDateTime.now());
        }
        record.setActionBy(currentUser(http));
        CloOptIn saved = optInRepo.save(record);
        auditLogService.log(optIn ? "CLO_OPT_IN" : "CLO_OPT_OUT",
                "CLO", "CloOptIn", saved.getId(), currentUser(http), "SUCCESS",
                (optIn ? "Opted in: " : "Opted out: ") + accountNumber);
        return ResponseEntity.ok(saved);
    }

    // ============================================================== SIMULATE REDEMPTION

    /**
     * Simulate a transaction and, if it matches an active offer for an
     * opted-in cardholder, record a CloRedemption and update the offer's
     * budget usage.
     *
     * Body: {accountNumber, cardId, merchantId, transactionAmount}
     *
     * <p>In production this would be triggered by a real POS webhook.
     * For now this endpoint lets admin demonstrate the flow.
     */
    @PostMapping("/simulate-redemption")
    public ResponseEntity<?> simulateRedemption(@RequestBody Map<String, Object> body, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        String accountNumber = String.valueOf(body.get("accountNumber"));
        Long cardId = body.get("cardId") != null ? Long.valueOf(String.valueOf(body.get("cardId"))) : null;
        Long merchantId = body.get("merchantId") != null ? Long.valueOf(String.valueOf(body.get("merchantId"))) : null;
        BigDecimal amount = new BigDecimal(String.valueOf(body.getOrDefault("transactionAmount", "0")));

        // 1. Check opt-in
        boolean optedIn = optInRepo.findByAccountNumber(accountNumber)
                .map(CloOptIn::getOptedIn).orElse(false);
        if (!optedIn) return ResponseEntity.badRequest().body(Map.of("error", "Client non opt-in au programme CLO"));

        // 2. Find the card
        PrepaidCard card = cardId != null ? cardRepo.findById(cardId).orElse(null) : null;
        if (card == null) return ResponseEntity.badRequest().body(Map.of("error", "Carte prépayée introuvable"));
        if (!card.getOwnerAccountNumber().equals(accountNumber)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cette carte n'appartient pas à ce client"));
        }

        // 3. Find a matching active offer at this merchant
        List<CloOffer> candidates = offerRepo.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .filter(o -> o.getStatus() == CloOfferStatus.ACTIVE)
                .filter(o -> o.getMinTransactionAmount() == null
                        || amount.compareTo(o.getMinTransactionAmount()) >= 0)
                .filter(o -> {
                    if (o.getTargetBins() == null || o.getTargetBins().isEmpty()) return true;
                    for (String bin : o.getTargetBins().split(",")) {
                        if (card.getBin().equals(bin.trim())) return true;
                    }
                    return false;
                })
                .filter(o -> o.getValidFrom() == null || !LocalDate.now().isBefore(o.getValidFrom()))
                .filter(o -> o.getValidTo() == null || !LocalDate.now().isAfter(o.getValidTo()))
                .collect(java.util.stream.Collectors.toList());

        if (candidates.isEmpty()) {
            return ResponseEntity.ok(Map.of("matched", false, "message", "Aucune offre CLO applicable"));
        }
        CloOffer offer = candidates.get(0);

        // 4. Budget check
        if (offer.getTotalBudget() != null
                && offer.getBudgetUsed().add(amount).compareTo(offer.getTotalBudget()) > 0) {
            offer.setStatus(CloOfferStatus.PAUSED);
            offerRepo.save(offer);
            return ResponseEntity.ok(Map.of("matched", false, "message", "Budget offre épuisé"));
        }

        // 5. Compute reward
        BigDecimal reward;
        if (offer.getRewardType() == CloRewardType.CASHBACK_PERCENT) {
            reward = amount.multiply(offer.getRewardValue()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        } else if (offer.getRewardType() == CloRewardType.CASHBACK_FIXED) {
            reward = offer.getRewardValue();
        } else {
            reward = offer.getRewardValue(); // points count as reward "value" for reporting
        }

        // 6. Record redemption
        CloRedemption r = new CloRedemption();
        r.setOffer(offer);
        r.setAccountNumber(accountNumber);
        r.setCardNumberMasked(card.getCardNumberMasked());
        r.setBin(card.getBin());
        r.setTransactionAmount(amount);
        r.setRewardAmount(reward);
        r.setRewardType(offer.getRewardType().name());
        r.setNotes("Simulation admin");
        redemptionRepo.save(r);

        offer.setBudgetUsed(offer.getBudgetUsed().add(reward));
        offer.setRedemptionCount(offer.getRedemptionCount() + 1);
        offerRepo.save(offer);

        auditLogService.log("CLO_REDEMPTION", "CLO", "CloRedemption", r.getId(),
                currentUser(http), "SUCCESS", "Redemption: " + reward + " " + offer.getRewardType() + " for " + accountNumber);

        return ResponseEntity.ok(Map.of(
                "matched", true,
                "offer", offer.getName(),
                "rewardType", offer.getRewardType().name(),
                "rewardValue", reward,
                "redemptionId", r.getId()
        ));
    }

    @GetMapping("/redemptions")
    public List<CloRedemption> listRedemptions() { return redemptionRepo.findAllByOrderByRedeemedAtDesc(); }

    // ============================================================== STATS

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long totalOffers = offerRepo.count();
        long activeOffers = offerRepo.countActive();
        long optedIn = optInRepo.countByOptedInTrue();
        long redemptions = redemptionRepo.count();
        BigDecimal totalRewards = redemptionRepo.findAll().stream()
                .map(CloRedemption::getRewardAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of(
                "totalOffers", totalOffers,
                "activeOffers", activeOffers,
                "optedInClients", optedIn,
                "totalRedemptions", redemptions,
                "totalRewardsDistributed", totalRewards
        );
    }
}
