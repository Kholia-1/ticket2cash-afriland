package com.afriland.ticket2cash.claim;

import com.afriland.ticket2cash.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Claims read/write with role-based scoping:
 * <ul>
 *   <li><b>ADMIN / OPERATEUR / LECTEUR</b>: see all claims across all merchants.</li>
 *   <li><b>PARTNER</b>: sees only claims for their own merchant (session-based).
 *       This prevents a partner from seeing another merchant's clients' claims.</li>
 * </ul>
 * A partner attempting to fetch claims for a merchant that is not theirs, or
 * to modify a claim that is not theirs, receives 403.
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimRepository claimRepository;
    private final AuditLogService auditLogService;

    public ClaimController(ClaimRepository claimRepository,
                           AuditLogService auditLogService) {
        this.claimRepository = claimRepository;
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

    private String currentUser(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        if (s == null) return "ANONYMOUS";
        Object u = s.getAttribute("AUTH_USERNAME");
        return u == null ? "ANONYMOUS" : String.valueOf(u);
    }

    private boolean isPartner(HttpServletRequest http) {
        return "PARTNER".equalsIgnoreCase(currentRole(http));
    }

    // ---------------------------------------------------------------- READ

    @GetMapping
    public List<Claim> getAllClaims(HttpServletRequest http) {
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null) return Collections.emptyList();
            return claimRepository.findByMerchantId(me);
        }
        // ADMIN / OPERATEUR / LECTEUR see everything
        return claimRepository.findAll();
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<?> getClaimsByMerchant(@PathVariable Long merchantId,
                                                  HttpServletRequest http) {
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null || !me.equals(merchantId)) {
                return ResponseEntity.status(403).body("Not your merchant");
            }
        }
        return ResponseEntity.ok(claimRepository.findByMerchantId(merchantId));
    }

    @GetMapping("/status/{status}")
    public List<Claim> getClaimsByStatus(@PathVariable ClaimStatus status,
                                          HttpServletRequest http) {
        if (isPartner(http)) {
            Long me = currentMerchantId(http);
            if (me == null) return Collections.emptyList();
            return claimRepository.findByMerchantId(me).stream()
                    .filter(c -> c.getStatus() == status)
                    .toList();
        }
        return claimRepository.findByStatus(status);
    }

    // ---------------------------------------------------------------- WRITE

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateClaimStatus(@PathVariable Long id,
                                                @RequestParam ClaimStatus status,
                                                HttpServletRequest http) {
        return claimRepository.findById(id)
                .map(claim -> {
                    // Partners can only touch their own claims
                    if (isPartner(http)) {
                        Long me = currentMerchantId(http);
                        if (me == null || !me.equals(claim.getMerchantId())) {
                            return ResponseEntity.status(403).body((Object) "Not your claim");
                        }
                    }
                    claim.setStatus(status);
                    Claim updated = claimRepository.save(claim);
                    auditLogService.log("UPDATE_CLAIM_STATUS", "CLAIM", "Claim",
                            updated.getId(), currentUser(http), "SUCCESS",
                            "Status → " + status);
                    return ResponseEntity.ok((Object) updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
