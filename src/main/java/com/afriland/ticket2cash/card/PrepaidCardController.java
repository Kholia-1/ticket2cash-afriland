package com.afriland.ticket2cash.card;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.common.ValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CRUD for Afriland prepaid cards. Admin only for writes.
 *
 * <p>The card number is never fully stored. When creating a card, the admin
 * supplies BIN (6 digits) and last4 (4 digits); the middle 6 are entered as
 * asterisks for masking. This keeps PCI scope minimal.
 */
@RestController
@RequestMapping("/api/prepaid-cards")
public class PrepaidCardController {

    private final PrepaidCardRepository repo;
    private final AuditLogService auditLogService;

    public PrepaidCardController(PrepaidCardRepository repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    // -------------------------------------------------------------- Auth

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

    // ============================================================== READ

    @GetMapping
    public List<PrepaidCard> list() { return repo.findAllByOrderByCreatedAtDesc(); }

    @GetMapping("/{id}")
    public ResponseEntity<PrepaidCard> byId(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/owner/{accountNumber}")
    public List<PrepaidCard> byOwner(@PathVariable String accountNumber) {
        return repo.findByOwnerAccountNumberOrderByCreatedAtDesc(accountNumber);
    }

    @GetMapping("/bins")
    public List<java.util.Map<String, String>> availableBins() {
        return AfrilandBinRegistry.asOptions();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long total = repo.count();
        long active = repo.countActive();
        List<Object[]> perBin = repo.countByBin();
        Map<String, Long> byBin = new java.util.LinkedHashMap<>();
        for (Object[] row : perBin) byBin.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        return Map.of(
                "total", total,
                "active", active,
                "byBin", byBin
        );
    }

    // ============================================================== CREATE

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PrepaidCard body, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));

        // BIN validation
        if (body.getBin() == null || !body.getBin().matches("\\d{6}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "BIN doit être exactement 6 chiffres", "field", "bin"));
        }
        AfrilandBinRegistry.BinEntry knownBin = AfrilandBinRegistry.BINS.stream()
                .filter(b -> b.bin.equals(body.getBin())).findFirst().orElse(null);
        if (knownBin == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "BIN inconnu — utilisez un BIN Afriland du registre", "field", "bin"));
        }

        // Last4 validation
        if (body.getLast4() != null && !body.getLast4().matches("\\d{4}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Les 4 derniers chiffres doivent être 4 digits", "field", "last4"));
        }

        String err = ValidationUtils.validateAccountNumber(body.getOwnerAccountNumber());
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "ownerAccountNumber"));

        err = ValidationUtils.validateAccountNumber(body.getLinkedBankAccount());
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "linkedBankAccount"));

        // Assemble masked card number and snapshot product name from BIN registry
        String last4 = body.getLast4() != null ? body.getLast4() : "0000";
        body.setCardNumberMasked(body.getBin() + "******" + last4);
        body.setProductName(knownBin.productName);
        body.setId(null);
        body.setCreatedBy(currentUser(http));

        PrepaidCard saved = repo.save(body);
        auditLogService.log("CREATE_PREPAID_CARD", "CARDS", "PrepaidCard", saved.getId(),
                currentUser(http), "SUCCESS", "Card issued: " + saved.getCardNumberMasked() + " to " + saved.getOwnerAccountNumber());
        return ResponseEntity.ok(saved);
    }

    // ============================================================== UPDATE

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PrepaidCard patch, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return repo.findById(id).map(existing -> {
            if (patch.getOwnerName() != null) existing.setOwnerName(patch.getOwnerName());
            if (patch.getOwnerPhone() != null) existing.setOwnerPhone(patch.getOwnerPhone());
            if (patch.getOwnerEmail() != null) existing.setOwnerEmail(patch.getOwnerEmail());
            if (patch.getStatus() != null) existing.setStatus(patch.getStatus());
            if (patch.getActivatedAt() != null) existing.setActivatedAt(patch.getActivatedAt());
            if (patch.getExpiresAt() != null) existing.setExpiresAt(patch.getExpiresAt());
            if (patch.getNotes() != null) existing.setNotes(patch.getNotes());
            PrepaidCard saved = repo.save(existing);
            auditLogService.log("UPDATE_PREPAID_CARD", "CARDS", "PrepaidCard", saved.getId(),
                    currentUser(http), "SUCCESS", "Card updated: " + saved.getCardNumberMasked());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return repo.findById(id).map(card -> {
            card.setStatus(PrepaidCardStatus.ACTIVE);
            if (card.getActivatedAt() == null) card.setActivatedAt(LocalDate.now());
            PrepaidCard saved = repo.save(card);
            auditLogService.log("ACTIVATE_PREPAID_CARD", "CARDS", "PrepaidCard", id,
                    currentUser(http), "SUCCESS", "Card activated: " + card.getCardNumberMasked());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<?> block(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        return repo.findById(id).map(card -> {
            card.setStatus(PrepaidCardStatus.BLOCKED);
            PrepaidCard saved = repo.save(card);
            auditLogService.log("BLOCK_PREPAID_CARD", "CARDS", "PrepaidCard", id,
                    currentUser(http), "SUCCESS", "Card blocked: " + card.getCardNumberMasked());
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) return ResponseEntity.status(403).body(Map.of("error", "Admin seulement"));
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        auditLogService.log("DELETE_PREPAID_CARD", "CARDS", "PrepaidCard", id,
                currentUser(http), "SUCCESS", "Card deleted");
        return ResponseEntity.noContent().build();
    }
}
