package com.afriland.ticket2cash.request;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.common.ValidationUtils;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Messaging endpoints between partners and admins.
 *
 * <p><b>Scoping:</b>
 * <ul>
 *   <li><b>PARTNER</b> — can create requests and view their own only.</li>
 *   <li><b>ADMIN</b> — can view all requests, respond, change status.</li>
 *   <li><b>OPERATEUR / LECTEUR</b> — read-only access to the inbox.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/partner-requests")
public class PartnerRequestController {

    private final PartnerRequestRepository repo;
    private final MerchantRepository merchantRepository;
    private final AuditLogService auditLogService;

    public PartnerRequestController(PartnerRequestRepository repo,
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

    // -------------------------------------------------------------- READ

    /** Admin: all requests. Partner: only theirs. Others: all (read-only). */
    @GetMapping
    public List<PartnerRequest> list(HttpServletRequest http) {
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null) return Collections.emptyList();
            return repo.findByMerchantIdOrderByCreatedAtDesc(me);
        }
        return repo.findAllByOrderByCreatedAtDesc();
    }

    /** Get one request by id (scoped for partner). */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, HttpServletRequest http) {
        return repo.findById(id)
                .map(r -> {
                    if (isPartner(http)) {
                        Long me = currentMerchantId(http);
                        if (me == null || !me.equals(r.getMerchantId())) {
                            return ResponseEntity.status(403).body((Object) "Not your request");
                        }
                    }
                    return ResponseEntity.ok((Object) r);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Simple stats endpoint for the admin nav badge. */
    @GetMapping("/stats")
    public Map<String, Object> stats(HttpServletRequest http) {
        long pending = repo.countPending();
        return Map.of("pending", pending);
    }

    // -------------------------------------------------------------- CREATE (partner only)

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest http) {
        if (!isPartner(http)) {
            return ResponseEntity.status(403).body(Map.of("error", "Seuls les partenaires peuvent envoyer une demande"));
        }
        Long myMid = currentMerchantId(http);
        if (myMid == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Aucun commerçant lié à votre compte"));
        }
        Merchant merchant = merchantRepository.findById(myMid).orElse(null);
        if (merchant == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Commerçant introuvable"));
        }

        String subject = String.valueOf(body.getOrDefault("subject", "")).trim();
        String message = String.valueOf(body.getOrDefault("message", "")).trim();
        String typeStr = String.valueOf(body.getOrDefault("type", "OTHER"));

        // Validation
        String err = ValidationUtils.validateName(subject, "sujet");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "subject"));

        if (message.length() < 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le message doit contenir au moins 5 caractères", "field", "message"));
        }
        if (message.length() > 4000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le message ne doit pas dépasser 4000 caractères", "field", "message"));
        }

        PartnerRequestType type;
        try { type = PartnerRequestType.valueOf(typeStr); }
        catch (Exception e) { type = PartnerRequestType.OTHER; }

        PartnerRequest r = new PartnerRequest();
        r.setMerchantId(myMid);
        r.setMerchantName(merchant.getName());
        r.setSenderUsername(currentUser(http));
        r.setType(type);
        r.setSubject(subject);
        r.setMessage(message);
        r.setStatus(PartnerRequestStatus.OPEN);

        PartnerRequest saved = repo.save(r);
        auditLogService.log("CREATE_PARTNER_REQUEST", "REQUEST", "PartnerRequest",
                saved.getId(), currentUser(http), "SUCCESS",
                "Partner request [" + type + "] from " + merchant.getName() + ": " + subject);

        return ResponseEntity.ok(saved);
    }

    // -------------------------------------------------------------- UPDATE STATUS (admin)

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestParam PartnerRequestStatus status,
                                          HttpServletRequest http) {
        if (!isAdmin(http)) {
            return ResponseEntity.status(403).body(Map.of("error", "Seul l'admin peut changer le statut"));
        }
        return repo.findById(id).map(r -> {
            r.setStatus(status);
            PartnerRequest saved = repo.save(r);
            auditLogService.log("UPDATE_REQUEST_STATUS", "REQUEST", "PartnerRequest",
                    id, currentUser(http), "SUCCESS", "Status → " + status);
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------- RESPOND (admin)

    @PostMapping("/{id}/respond")
    public ResponseEntity<?> respond(@PathVariable Long id,
                                     @RequestBody Map<String, Object> body,
                                     HttpServletRequest http) {
        if (!isAdmin(http)) {
            return ResponseEntity.status(403).body(Map.of("error", "Seul l'admin peut répondre"));
        }
        String response = String.valueOf(body.getOrDefault("response", "")).trim();
        if (response.length() < 3) {
            return ResponseEntity.badRequest().body(Map.of("error", "La réponse doit contenir au moins 3 caractères", "field", "response"));
        }
        if (response.length() > 4000) {
            return ResponseEntity.badRequest().body(Map.of("error", "La réponse ne doit pas dépasser 4000 caractères", "field", "response"));
        }

        return repo.findById(id).map(r -> {
            r.setAdminResponse(response);
            r.setResponderUsername(currentUser(http));
            r.setStatus(PartnerRequestStatus.RESPONDED);
            r.setRespondedAt(LocalDateTime.now());
            PartnerRequest saved = repo.save(r);
            auditLogService.log("RESPOND_REQUEST", "REQUEST", "PartnerRequest",
                    id, currentUser(http), "SUCCESS", "Response sent");
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------- DELETE (admin)

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest http) {
        if (!isAdmin(http)) {
            return ResponseEntity.status(403).body(Map.of("error", "Seul l'admin peut supprimer"));
        }
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        auditLogService.log("DELETE_PARTNER_REQUEST", "REQUEST", "PartnerRequest",
                id, currentUser(http), "SUCCESS", "Request deleted");
        return ResponseEntity.noContent().build();
    }
}
