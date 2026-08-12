package com.afriland.ticket2cash.loyalty;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.common.ValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CRUD for loyalty tiers (GL-07). Also seeds a sensible default set of 4 tiers
 * on the first GET when the database is empty — so admins don't start from
 * scratch and the demo works out of the box.
 */
@RestController
@RequestMapping("/api/loyalty/tiers")
public class LoyaltyTierController {

    private final LoyaltyTierRepository repo;
    private final AuditLogService auditLogService;

    public LoyaltyTierController(LoyaltyTierRepository repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    // -------------------------------------------------------------- helpers

    private boolean isAdmin(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        if (s == null) return false;
        return "ADMIN".equalsIgnoreCase(String.valueOf(s.getAttribute("AUTH_ROLE")));
    }

    private String currentUser(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        if (s == null) return "ANONYMOUS";
        Object u = s.getAttribute("AUTH_USERNAME");
        return u == null ? "ANONYMOUS" : String.valueOf(u);
    }

    /** Seed the default 4 tiers if none exist yet. Called on first GET. */
    private void ensureDefaultTiers() {
        if (repo.count() > 0) return;
        create("Essentiel", "Niveau de départ pour tous les clients Afriland.", 0,
                BigDecimal.ZERO, 0, new BigDecimal("0.0"),
                "• Cashback de base sur les campagnes actives",
                "#94A3B8", "🥉");
        create("Premium", "Client fidèle avec activité régulière.", 1,
                new BigDecimal("500000"), 10, new BigDecimal("0.5"),
                "• +0.5% de cashback bonus\n• Priorité support client\n• Accès aux offres partenaires exclusives",
                "#F59E0B", "🥈");
        create("Prestige", "Client VIP avec fort volume de transactions.", 2,
                new BigDecimal("2000000"), 30, new BigDecimal("1.0"),
                "• +1% de cashback bonus\n• Ligne dédiée VIP\n• Événements privés\n• Cadeaux d'anniversaire",
                "#EAB308", "🥇");
        create("Elite", "Clientèle très haut de gamme, la crème.", 3,
                new BigDecimal("10000000"), 60, new BigDecimal("2.0"),
                "• +2% de cashback bonus\n• Concierge personnel\n• Salons aéroports\n• Cadeaux d'anniversaire premium\n• Invitations événements Afriland",
                "#C1121F", "💎");
    }

    private void create(String name, String desc, int order, BigDecimal minSpend,
                        int minTx, BigDecimal bonus, String benefits, String color, String icon) {
        LoyaltyTier t = new LoyaltyTier();
        t.setName(name); t.setDescription(desc); t.setSortOrder(order);
        t.setMinCumulativeSpend(minSpend); t.setMinTransactionCount(minTx);
        t.setCashbackBonusPercent(bonus); t.setBenefitsSummary(benefits);
        t.setColorHex(color); t.setIcon(icon);
        t.setEvaluationMonths(12); t.setGraceMonths(6); t.setActive(true);
        repo.save(t);
    }

    // -------------------------------------------------------------- READ

    @GetMapping
    public List<LoyaltyTier> list() {
        ensureDefaultTiers();
        return repo.findAllByOrderBySortOrderAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoyaltyTier> byId(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------- WRITE (admin only)

    @PostMapping
    public ResponseEntity<?> create(@RequestBody LoyaltyTier tier, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        String err = ValidationUtils.validateName(tier.getName(), "nom du niveau");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));

        if (repo.existsByNameIgnoreCase(tier.getName().trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Un niveau avec ce nom existe déjà", "field", "name"));
        }
        err = ValidationUtils.validatePositiveOrZero(tier.getMinCumulativeSpend(), "seuil de dépense");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "minCumulativeSpend"));

        err = ValidationUtils.validatePercentage(tier.getCashbackBonusPercent(), "bonus cashback");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "cashbackBonusPercent"));

        tier.setId(null);
        tier.setName(tier.getName().trim());
        LoyaltyTier saved = repo.save(tier);
        auditLogService.log("CREATE_TIER", "LOYALTY", "LoyaltyTier", saved.getId(),
                currentUser(http), "SUCCESS", "Tier created: " + saved.getName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody LoyaltyTier patch, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return repo.findById(id).map(existing -> {
            if (patch.getName() != null) {
                String err = ValidationUtils.validateName(patch.getName(), "nom du niveau");
                if (err != null) return ResponseEntity.badRequest().body((Object) Map.of("error", err, "field", "name"));
                existing.setName(patch.getName().trim());
            }
            if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
            if (patch.getSortOrder() != null) existing.setSortOrder(patch.getSortOrder());
            if (patch.getMinCumulativeSpend() != null) {
                String err = ValidationUtils.validatePositiveOrZero(patch.getMinCumulativeSpend(), "seuil de dépense");
                if (err != null) return ResponseEntity.badRequest().body((Object) Map.of("error", err, "field", "minCumulativeSpend"));
                existing.setMinCumulativeSpend(patch.getMinCumulativeSpend());
            }
            if (patch.getMinTransactionCount() != null) existing.setMinTransactionCount(patch.getMinTransactionCount());
            if (patch.getEvaluationMonths() != null) existing.setEvaluationMonths(patch.getEvaluationMonths());
            if (patch.getGraceMonths() != null) existing.setGraceMonths(patch.getGraceMonths());
            if (patch.getCashbackBonusPercent() != null) {
                String err = ValidationUtils.validatePercentage(patch.getCashbackBonusPercent(), "bonus cashback");
                if (err != null) return ResponseEntity.badRequest().body((Object) Map.of("error", err, "field", "cashbackBonusPercent"));
                existing.setCashbackBonusPercent(patch.getCashbackBonusPercent());
            }
            if (patch.getBenefitsSummary() != null) existing.setBenefitsSummary(patch.getBenefitsSummary());
            if (patch.getColorHex() != null) existing.setColorHex(patch.getColorHex());
            if (patch.getIcon() != null) existing.setIcon(patch.getIcon());
            if (patch.getActive() != null) existing.setActive(patch.getActive());
            LoyaltyTier saved = repo.save(existing);
            auditLogService.log("UPDATE_TIER", "LOYALTY", "LoyaltyTier", saved.getId(),
                    currentUser(http), "SUCCESS", "Tier updated: " + saved.getName());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        auditLogService.log("DELETE_TIER", "LOYALTY", "LoyaltyTier", id,
                currentUser(http), "SUCCESS", "Tier deleted");
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- Progression evaluation

    /**
     * Given a client's cumulative spend and transaction count, returns which tier
     * they qualify for. Simple max-match: they get the highest tier whose
     * thresholds they meet.
     */
    @GetMapping("/evaluate")
    public Map<String, Object> evaluate(@RequestParam BigDecimal cumulativeSpend,
                                         @RequestParam Integer transactionCount) {
        List<LoyaltyTier> tiers = repo.findByActiveTrueOrderBySortOrderAsc();
        LoyaltyTier qualified = null;
        LoyaltyTier next = null;
        for (LoyaltyTier t : tiers) {
            boolean spendOk = t.getMinCumulativeSpend() == null
                    || cumulativeSpend.compareTo(t.getMinCumulativeSpend()) >= 0;
            boolean txOk = t.getMinTransactionCount() == null
                    || transactionCount >= t.getMinTransactionCount();
            if (spendOk && txOk) qualified = t;
            else if (qualified != null && next == null) next = t;
        }
        BigDecimal progressPercent = BigDecimal.ZERO;
        BigDecimal spendToNext = BigDecimal.ZERO;
        if (next != null) {
            BigDecimal target = next.getMinCumulativeSpend();
            spendToNext = target.subtract(cumulativeSpend).max(BigDecimal.ZERO);
            if (target.signum() > 0) {
                progressPercent = cumulativeSpend.multiply(BigDecimal.valueOf(100))
                        .divide(target, 2, java.math.RoundingMode.HALF_UP);
                if (progressPercent.compareTo(BigDecimal.valueOf(100)) > 0)
                    progressPercent = BigDecimal.valueOf(100);
            }
        }
        return Map.of(
                "currentTier", qualified,
                "nextTier", next != null ? next : "",
                "progressPercent", progressPercent,
                "spendToNext", spendToNext
        );
    }
}
