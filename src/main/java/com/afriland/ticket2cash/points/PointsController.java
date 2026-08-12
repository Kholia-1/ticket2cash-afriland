package com.afriland.ticket2cash.points;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.common.ValidationUtils;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the points system (GL-02).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li><b>/accounts</b> — list, per-account view, per-account history</li>
 *   <li><b>/accounts/{accountNumber}/credit</b> — manual admin credit</li>
 *   <li><b>/accounts/{accountNumber}/burn</b> — burn / redeem points</li>
 *   <li><b>/rules</b> — CRUD points rules</li>
 *   <li><b>/expire-check</b> — trigger the expiration sweep</li>
 *   <li><b>/stats</b> — aggregate points KPIs</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final PointsAccountRepository accountRepo;
    private final PointsTransactionRepository txRepo;
    private final PointsRuleRepository ruleRepo;
    private final MerchantRepository merchantRepo;
    private final AuditLogService auditLogService;

    public PointsController(PointsAccountRepository accountRepo,
                            PointsTransactionRepository txRepo,
                            PointsRuleRepository ruleRepo,
                            MerchantRepository merchantRepo,
                            AuditLogService auditLogService) {
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.ruleRepo = ruleRepo;
        this.merchantRepo = merchantRepo;
        this.auditLogService = auditLogService;
    }

    // -------------------------------------------------------------- Auth helpers

    private boolean isAdmin(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        return s != null && "ADMIN".equalsIgnoreCase(String.valueOf(s.getAttribute("AUTH_ROLE")));
    }

    private String currentUser(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        if (s == null) return "ANONYMOUS";
        Object u = s.getAttribute("AUTH_USERNAME");
        return u == null ? "ANONYMOUS" : String.valueOf(u);
    }

    // ============================================================== ACCOUNTS

    @GetMapping("/accounts")
    public List<PointsAccount> listAccounts() {
        return accountRepo.findAllByOrderByBalanceDesc();
    }

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<PointsAccount> getAccount(@PathVariable String accountNumber) {
        return accountRepo.findByAccountNumber(accountNumber)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/accounts/{accountNumber}/history")
    public List<PointsTransaction> accountHistory(@PathVariable String accountNumber) {
        return txRepo.findByAccountNumberOrderByCreatedAtDesc(accountNumber);
    }

    /**
     * Manually credit points to an account. Admin only.
     * Body: {points, description, ownerName?}
     */
    @PostMapping("/accounts/{accountNumber}/credit")
    public ResponseEntity<?> credit(@PathVariable String accountNumber,
                                     @RequestBody Map<String, Object> body,
                                     HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        long points;
        try { points = Long.parseLong(String.valueOf(body.getOrDefault("points", "0"))); }
        catch (NumberFormatException e) { return ResponseEntity.badRequest().body(Map.of("error", "points invalides")); }
        if (points <= 0) return ResponseEntity.badRequest().body(Map.of("error", "points doit être > 0", "field", "points"));

        String description = String.valueOf(body.getOrDefault("description", "Crédit manuel"));
        String ownerName = body.get("ownerName") != null ? String.valueOf(body.get("ownerName")) : null;
        Integer validityMonths = body.get("validityMonths") != null
                ? Integer.parseInt(String.valueOf(body.get("validityMonths"))) : 12;

        PointsAccount acc = accountRepo.findByAccountNumber(accountNumber)
                .orElseGet(() -> accountRepo.save(new PointsAccount(accountNumber, ownerName)));
        if (ownerName != null && !ownerName.isEmpty()) acc.setOwnerName(ownerName);

        PointsTransaction ptx = new PointsTransaction();
        ptx.setAccountNumber(accountNumber);
        ptx.setType(PointsTransactionType.EARN);
        ptx.setPoints(points);
        ptx.setExpiresAt(LocalDateTime.now().plusMonths(validityMonths));
        ptx.setSource(PointsSource.MANUAL_ADJUST);
        ptx.setDescription(description);
        ptx.setCreatedBy(currentUser(http));
        txRepo.save(ptx);

        acc.setBalance(acc.getBalance() + points);
        acc.setTotalEarned(acc.getTotalEarned() + points);
        accountRepo.save(acc);

        auditLogService.log("POINTS_CREDIT", "POINTS", "PointsAccount", acc.getId(),
                currentUser(http), "SUCCESS", "+" + points + " pts to " + accountNumber);
        return ResponseEntity.ok(Map.of("account", acc, "transaction", ptx));
    }

    /**
     * Burn points from an account. Admin only.
     * Body: {points, description}
     */
    @PostMapping("/accounts/{accountNumber}/burn")
    public ResponseEntity<?> burn(@PathVariable String accountNumber,
                                   @RequestBody Map<String, Object> body,
                                   HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        long points;
        try { points = Long.parseLong(String.valueOf(body.getOrDefault("points", "0"))); }
        catch (NumberFormatException e) { return ResponseEntity.badRequest().body(Map.of("error", "points invalides")); }
        if (points <= 0) return ResponseEntity.badRequest().body(Map.of("error", "points doit être > 0", "field", "points"));

        String description = String.valueOf(body.getOrDefault("description", "Redemption"));

        PointsAccount acc = accountRepo.findByAccountNumber(accountNumber).orElse(null);
        if (acc == null) return ResponseEntity.badRequest().body(Map.of("error", "Compte points inexistant"));
        if (acc.getBalance() < points) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Solde insuffisant (" + acc.getBalance() + " points disponibles)"));
        }

        PointsTransaction ptx = new PointsTransaction();
        ptx.setAccountNumber(accountNumber);
        ptx.setType(PointsTransactionType.BURN);
        ptx.setPoints(-points);
        ptx.setSource(PointsSource.MANUAL_ADJUST);
        ptx.setDescription(description);
        ptx.setCreatedBy(currentUser(http));
        txRepo.save(ptx);

        acc.setBalance(acc.getBalance() - points);
        acc.setTotalBurned(acc.getTotalBurned() + points);
        accountRepo.save(acc);

        auditLogService.log("POINTS_BURN", "POINTS", "PointsAccount", acc.getId(),
                currentUser(http), "SUCCESS", "-" + points + " pts from " + accountNumber);
        return ResponseEntity.ok(Map.of("account", acc, "transaction", ptx));
    }

    // ============================================================== RULES

    @GetMapping("/rules")
    public List<PointsRule> listRules() {
        return ruleRepo.findAllByOrderByPriorityDescIdAsc();
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<PointsRule> getRule(@PathVariable Long id) {
        return ruleRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    public ResponseEntity<?> createRule(@RequestBody PointsRule rule, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        String err = ValidationUtils.validateName(rule.getName(), "nom de la règle");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));

        if (rule.getPointsPer1000Fcfa() == null || rule.getPointsPer1000Fcfa() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Taux points par 1000 FCFA doit être > 0", "field", "pointsPer1000Fcfa"));
        }
        if (rule.getMerchant() != null && rule.getMerchant().getId() != null) {
            Merchant m = merchantRepo.findById(rule.getMerchant().getId()).orElse(null);
            if (m == null) return ResponseEntity.badRequest().body(Map.of("error", "Marchand introuvable"));
            rule.setMerchant(m);
        }
        rule.setId(null);
        rule.setName(rule.getName().trim());
        rule.setCreatedBy(currentUser(http));
        PointsRule saved = ruleRepo.save(rule);
        auditLogService.log("CREATE_POINTS_RULE", "POINTS", "PointsRule", saved.getId(),
                currentUser(http), "SUCCESS", "Rule created: " + saved.getName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody PointsRule patch, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return ruleRepo.findById(id).map(existing -> {
            if (patch.getName() != null) existing.setName(patch.getName().trim());
            if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
            if (patch.getPointsPer1000Fcfa() != null) existing.setPointsPer1000Fcfa(patch.getPointsPer1000Fcfa());
            if (patch.getMultiplier() != null) existing.setMultiplier(patch.getMultiplier());
            if (patch.getValidityMonths() != null) existing.setValidityMonths(patch.getValidityMonths());
            if (patch.getMinSpendAmount() != null) existing.setMinSpendAmount(patch.getMinSpendAmount());
            if (patch.getMerchant() != null && patch.getMerchant().getId() != null) {
                merchantRepo.findById(patch.getMerchant().getId()).ifPresent(existing::setMerchant);
            }
            if (patch.getMccCode() != null) existing.setMccCode(patch.getMccCode());
            if (patch.getProductSku() != null) existing.setProductSku(patch.getProductSku());
            if (patch.getPriority() != null) existing.setPriority(patch.getPriority());
            if (patch.getActive() != null) existing.setActive(patch.getActive());
            if (patch.getStartDate() != null) existing.setStartDate(patch.getStartDate());
            if (patch.getEndDate() != null) existing.setEndDate(patch.getEndDate());
            PointsRule saved = ruleRepo.save(existing);
            auditLogService.log("UPDATE_POINTS_RULE", "POINTS", "PointsRule", saved.getId(),
                    currentUser(http), "SUCCESS", "Rule updated: " + saved.getName());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        if (!ruleRepo.existsById(id)) return ResponseEntity.notFound().build();
        ruleRepo.deleteById(id);
        auditLogService.log("DELETE_POINTS_RULE", "POINTS", "PointsRule", id,
                currentUser(http), "SUCCESS", "Rule deleted");
        return ResponseEntity.noContent().build();
    }

    // ============================================================== APPLY RULES (simulate earn from transaction)

    /**
     * Given a hypothetical transaction, compute the points that would be earned.
     * Useful for previews and for the LoyaltyCalculatorService integration.
     *
     * Body: {accountNumber, amount, merchantId?, mccCode?, sku?}
     */
    @PostMapping("/simulate")
    public Map<String, Object> simulate(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.getOrDefault("amount", "0")));
        Long merchantId = body.get("merchantId") != null ? Long.valueOf(String.valueOf(body.get("merchantId"))) : null;
        String mccCode = body.get("mccCode") != null ? String.valueOf(body.get("mccCode")) : null;
        String sku = body.get("sku") != null ? String.valueOf(body.get("sku")) : null;

        List<PointsRule> rules = ruleRepo.findByActiveTrueOrderByPriorityDescIdAsc();
        PointsRule matched = null;
        for (PointsRule r : rules) {
            if (r.getMinSpendAmount() != null && amount.compareTo(r.getMinSpendAmount()) < 0) continue;
            if (r.getMerchant() != null && (merchantId == null || !r.getMerchant().getId().equals(merchantId))) continue;
            if (r.getMccCode() != null && (mccCode == null || !r.getMccCode().name().equals(mccCode))) continue;
            if (r.getProductSku() != null && (sku == null || !r.getProductSku().contains(sku))) continue;
            matched = r;
            break; // priority order → take the first match
        }
        if (matched == null) return Map.of("pointsEarned", 0, "ruleMatched", "");

        BigDecimal baseAmount = amount.divide(BigDecimal.valueOf(1000), 4, java.math.RoundingMode.DOWN);
        BigDecimal points = baseAmount.multiply(BigDecimal.valueOf(matched.getPointsPer1000Fcfa()));
        if (matched.getMultiplier() != null) points = points.multiply(matched.getMultiplier());
        long finalPoints = points.setScale(0, java.math.RoundingMode.DOWN).longValueExact();

        return Map.of(
                "pointsEarned", finalPoints,
                "ruleMatched", matched.getName(),
                "ruleId", matched.getId(),
                "baseRate", matched.getPointsPer1000Fcfa(),
                "multiplier", matched.getMultiplier() != null ? matched.getMultiplier() : BigDecimal.ONE
        );
    }

    // ============================================================== EXPIRATION SWEEP

    /**
     * Sweep all EARN transactions past their expiresAt date without matching
     * BURN entries. Creates an EXPIRE ledger entry and reduces the balance.
     *
     * <p>Simple LIFO: we don't attempt to reconcile partial burns, we just
     * check the total balance and expire the remainder. This is a common
     * pragmatic simplification for MVP.
     */
    @PostMapping("/expire-check")
    public Map<String, Object> expireCheck(HttpServletRequest http) {
        if (!isAdmin(http)) return Map.of("error", "Admin seulement");
        LocalDateTime now = LocalDateTime.now();
        List<PointsTransaction> expired = txRepo.findByTypeAndExpiresAtBeforeOrderByExpiresAtAsc(
                PointsTransactionType.EARN, now);

        long totalExpired = 0;
        int accountsAffected = 0;
        java.util.Set<String> touched = new java.util.HashSet<>();
        for (PointsTransaction earn : expired) {
            PointsAccount acc = accountRepo.findByAccountNumber(earn.getAccountNumber()).orElse(null);
            if (acc == null) continue;
            // Skip if we've already fully swept this account for this cycle
            // (naive: we just expire one point-block per account per sweep)
            if (acc.getBalance() <= 0) continue;

            long amt = Math.min(earn.getPoints(), acc.getBalance());
            if (amt <= 0) continue;

            PointsTransaction sweep = new PointsTransaction();
            sweep.setAccountNumber(earn.getAccountNumber());
            sweep.setType(PointsTransactionType.EXPIRE);
            sweep.setPoints(-amt);
            sweep.setSource(PointsSource.EXPIRATION_SWEEP);
            sweep.setDescription("Expiration automatique de " + amt + " points (crédités le " + earn.getCreatedAt().toLocalDate() + ")");
            sweep.setCreatedBy("SYSTEM");
            sweep.setSourceLoyaltyTransactionId(earn.getId());
            txRepo.save(sweep);

            acc.setBalance(acc.getBalance() - amt);
            acc.setTotalExpired(acc.getTotalExpired() + amt);
            acc.setLastExpirationCheck(now);
            accountRepo.save(acc);

            totalExpired += amt;
            if (touched.add(earn.getAccountNumber())) accountsAffected++;

            // mark the source EARN as processed by nulling its expiresAt (idempotency guard)
            earn.setExpiresAt(null);
            txRepo.save(earn);
        }

        auditLogService.log("POINTS_EXPIRE_SWEEP", "POINTS", "PointsAccount", null,
                currentUser(http), "SUCCESS", totalExpired + " points expired across " + accountsAffected + " accounts");

        return Map.of(
                "totalPointsExpired", totalExpired,
                "accountsAffected", accountsAffected,
                "runAt", now.toString()
        );
    }

    // ============================================================== STATS

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long accounts = accountRepo.count();
        long rules = ruleRepo.count();
        Long sumBalance = accountRepo.sumAllBalances();
        Long sumEarned = accountRepo.sumAllEarned();
        Long sumBurned = accountRepo.sumAllBurned();
        return Map.of(
                "totalAccounts", accounts,
                "activeRules", rules,
                "totalPointsCirculating", sumBalance == null ? 0 : sumBalance,
                "totalPointsEarned", sumEarned == null ? 0 : sumEarned,
                "totalPointsBurned", sumBurned == null ? 0 : sumBurned,
                "redemptionRate", (sumEarned != null && sumEarned > 0)
                        ? (100.0 * (sumBurned == null ? 0 : sumBurned) / sumEarned) : 0.0
        );
    }
}
