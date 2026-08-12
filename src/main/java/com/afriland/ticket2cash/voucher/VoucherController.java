package com.afriland.ticket2cash.voucher;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.common.ValidationUtils;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Voucher engine (GL-06). Admin creates vouchers, sets rules, tracks status.
 * The customer view — showing vouchers for a specific customer — is currently
 * a separate "my vouchers" query.
 *
 * <p><b>Scoping:</b>
 * <ul>
 *   <li><b>ADMIN</b> — full CRUD, statistics, manual consumption.</li>
 *   <li><b>PARTNER</b> — read-only view of vouchers targeting their own merchant.</li>
 *   <li>Others — read all.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

    private final VoucherRepository repo;
    private final MerchantRepository merchantRepository;
    private final AuditLogService auditLogService;

    private static final char[] CODE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // no ambiguous chars
    private static final SecureRandom RNG = new SecureRandom();

    public VoucherController(VoucherRepository repo,
                             MerchantRepository merchantRepository,
                             AuditLogService auditLogService) {
        this.repo = repo;
        this.merchantRepository = merchantRepository;
        this.auditLogService = auditLogService;
    }

    // -------------------------------------------------------------- helpers

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

    private boolean isPartner(HttpServletRequest http) {
        return "PARTNER".equalsIgnoreCase(currentRole(http));
    }

    /** Generate a unique voucher code like "VCH-A7X4-KP9M". */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("VCH-");
            for (int i = 0; i < 4; i++) sb.append(CODE_ALPHABET[RNG.nextInt(CODE_ALPHABET.length)]);
            sb.append('-');
            for (int i = 0; i < 4; i++) sb.append(CODE_ALPHABET[RNG.nextInt(CODE_ALPHABET.length)]);
            String candidate = sb.toString();
            if (!repo.existsByCode(candidate)) return candidate;
        }
        // Fallback with timestamp
        return "VCH-" + System.currentTimeMillis();
    }

    // -------------------------------------------------------------- READ

    @GetMapping
    public List<Voucher> list(HttpServletRequest http) {
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null) return Collections.emptyList();
            return repo.findByMerchantIdOrderByCreatedAtDesc(me);
        }
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Voucher> byId(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Get vouchers assigned to a specific customer account. */
    @GetMapping("/owner/{accountNumber}")
    public List<Voucher> byOwner(@PathVariable String accountNumber) {
        return repo.findByOwnerAccountNumberOrderByCreatedAtDesc(accountNumber);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long total = repo.count();
        long issued = repo.countIssued();
        long consumed = repo.countConsumed();
        return Map.of(
                "total", total,
                "issued", issued,
                "consumed", consumed,
                "redemptionRate", total > 0 ? (100.0 * consumed / total) : 0.0
        );
    }

    // -------------------------------------------------------------- CREATE

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Voucher body, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        String err = ValidationUtils.validateName(body.getName(), "nom du voucher");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));

        if (body.getType() == null) return ResponseEntity.badRequest().body(Map.of("error", "Type de voucher requis", "field", "type"));
        if (body.getValueType() == null) return ResponseEntity.badRequest().body(Map.of("error", "Type de valeur requis", "field", "valueType"));

        if (body.getValueType() != VoucherValueType.FREE_PRODUCT) {
            if (body.getValue() == null) return ResponseEntity.badRequest().body(Map.of("error", "Valeur requise", "field", "value"));
            err = (body.getValueType() == VoucherValueType.PERCENTAGE_DISCOUNT)
                    ? ValidationUtils.validatePercentage(body.getValue(), "pourcentage")
                    : ValidationUtils.validatePositive(body.getValue(), "montant");
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "value"));
        }

        err = ValidationUtils.validateDateRange(body.getValidFrom(), body.getValidTo());
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "validTo"));

        // Optional merchant link
        if (body.getMerchant() != null && body.getMerchant().getId() != null) {
            Merchant m = merchantRepository.findById(body.getMerchant().getId()).orElse(null);
            if (m == null) return ResponseEntity.badRequest().body(Map.of("error", "Commerçant introuvable"));
            body.setMerchant(m);
        }

        body.setId(null);
        body.setCode(generateUniqueCode());
        body.setName(body.getName().trim());
        body.setStatus(VoucherStatus.ISSUED);
        body.setCurrentUses(0);
        body.setCreatedBy(currentUser(http));

        Voucher saved = repo.save(body);
        auditLogService.log("CREATE_VOUCHER", "VOUCHER", "Voucher", saved.getId(),
                currentUser(http), "SUCCESS", "Voucher created: " + saved.getCode() + " - " + saved.getName());
        return ResponseEntity.ok(saved);
    }

    /**
     * Bulk generation: create N vouchers of the same template.
     * Body: {name, type, valueType, value, validFrom, validTo, usageMode, count, merchantId?}
     */
    @PostMapping("/bulk")
    public ResponseEntity<?> bulkCreate(@RequestBody Map<String, Object> body, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        int count;
        try {
            count = Integer.parseInt(String.valueOf(body.getOrDefault("count", "0")));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", "Nombre invalide")); }
        if (count < 1 || count > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Count doit être entre 1 et 1000"));
        }
        String name = String.valueOf(body.getOrDefault("name", ""));
        String err = ValidationUtils.validateName(name, "nom du voucher");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));

        VoucherType type;
        try { type = VoucherType.valueOf(String.valueOf(body.getOrDefault("type", "ONE_SHOT"))); }
        catch (Exception e) { type = VoucherType.ONE_SHOT; }

        VoucherValueType vType;
        try { vType = VoucherValueType.valueOf(String.valueOf(body.getOrDefault("valueType", "PERCENTAGE_DISCOUNT"))); }
        catch (Exception e) { vType = VoucherValueType.PERCENTAGE_DISCOUNT; }

        BigDecimal value = null;
        Object rawVal = body.get("value");
        if (rawVal != null) {
            try { value = new BigDecimal(String.valueOf(rawVal)); } catch (Exception ignored) {}
        }
        LocalDate validFrom = body.get("validFrom") != null ? LocalDate.parse(String.valueOf(body.get("validFrom"))) : null;
        LocalDate validTo = body.get("validTo") != null ? LocalDate.parse(String.valueOf(body.get("validTo"))) : null;

        Merchant merchant = null;
        if (body.get("merchantId") != null) {
            try {
                merchant = merchantRepository.findById(Long.valueOf(String.valueOf(body.get("merchantId")))).orElse(null);
            } catch (Exception ignored) {}
        }

        int created = 0;
        for (int i = 0; i < count; i++) {
            Voucher v = new Voucher();
            v.setCode(generateUniqueCode());
            v.setName(name.trim());
            v.setType(type);
            v.setValueType(vType);
            v.setValue(value);
            v.setValidFrom(validFrom);
            v.setValidTo(validTo);
            v.setMerchant(merchant);
            v.setStatus(VoucherStatus.ISSUED);
            v.setUsageMode(VoucherUsageMode.SINGLE_USE);
            v.setCurrentUses(0);
            v.setCreatedBy(currentUser(http));
            repo.save(v);
            created++;
        }
        auditLogService.log("BULK_CREATE_VOUCHERS", "VOUCHER", "Voucher", null,
                currentUser(http), "SUCCESS", "Bulk created " + created + " vouchers of type " + type);
        return ResponseEntity.ok(Map.of("created", created, "type", type.name()));
    }

    // -------------------------------------------------------------- UPDATE

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Voucher patch, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return repo.findById(id).map(existing -> {
            if (patch.getName() != null) {
                String err = ValidationUtils.validateName(patch.getName(), "nom du voucher");
                if (err != null) return ResponseEntity.badRequest().body((Object) Map.of("error", err, "field", "name"));
                existing.setName(patch.getName().trim());
            }
            if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
            if (patch.getValidFrom() != null) existing.setValidFrom(patch.getValidFrom());
            if (patch.getValidTo() != null) existing.setValidTo(patch.getValidTo());
            String err = ValidationUtils.validateDateRange(existing.getValidFrom(), existing.getValidTo());
            if (err != null) return ResponseEntity.badRequest().body((Object) Map.of("error", err, "field", "validTo"));

            if (patch.getStatus() != null) existing.setStatus(patch.getStatus());
            if (patch.getOwnerAccountNumber() != null) existing.setOwnerAccountNumber(patch.getOwnerAccountNumber());
            if (patch.getOwnerName() != null) existing.setOwnerName(patch.getOwnerName());
            if (patch.getMaxUses() != null) existing.setMaxUses(patch.getMaxUses());
            if (patch.getUsageMode() != null) existing.setUsageMode(patch.getUsageMode());
            if (patch.getNotes() != null) existing.setNotes(patch.getNotes());
            Voucher saved = repo.save(existing);
            auditLogService.log("UPDATE_VOUCHER", "VOUCHER", "Voucher", saved.getId(),
                    currentUser(http), "SUCCESS", "Voucher updated: " + saved.getCode());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Manually consume a voucher (admin action). */
    @PostMapping("/{id}/consume")
    public ResponseEntity<?> consume(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return repo.findById(id).map(v -> {
            if (v.getStatus() != VoucherStatus.ISSUED) {
                return ResponseEntity.badRequest().body((Object) Map.of("error", "Voucher n'est pas dans l'état ISSUED"));
            }
            v.setCurrentUses(v.getCurrentUses() + 1);
            if (v.getUsageMode() == VoucherUsageMode.SINGLE_USE
                    || (v.getMaxUses() != null && v.getCurrentUses() >= v.getMaxUses())) {
                v.setStatus(VoucherStatus.CONSUMED);
                v.setConsumedAt(LocalDateTime.now());
            }
            Voucher saved = repo.save(v);
            auditLogService.log("CONSUME_VOUCHER", "VOUCHER", "Voucher", id,
                    currentUser(http), "SUCCESS", "Voucher consumed: " + v.getCode());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return repo.findById(id).map(v -> {
            v.setStatus(VoucherStatus.CANCELLED);
            Voucher saved = repo.save(v);
            auditLogService.log("CANCEL_VOUCHER", "VOUCHER", "Voucher", id,
                    currentUser(http), "SUCCESS", "Voucher cancelled: " + v.getCode());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------- DELETE

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        auditLogService.log("DELETE_VOUCHER", "VOUCHER", "Voucher", id,
                currentUser(http), "SUCCESS", "Voucher deleted");
        return ResponseEntity.noContent().build();
    }
}
