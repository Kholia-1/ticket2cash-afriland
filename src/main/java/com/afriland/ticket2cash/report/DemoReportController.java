package com.afriland.ticket2cash.report;

import com.afriland.ticket2cash.audit.AuditLog;
import com.afriland.ticket2cash.audit.AuditLogRepository;
import com.afriland.ticket2cash.auth.AppUser;
import com.afriland.ticket2cash.auth.AppUserRepository;
import com.afriland.ticket2cash.auth.UserRole;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.cashback.CashbackPaymentStatus;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.fraud.FraudAlertRepository;
import com.afriland.ticket2cash.fraud.FraudAlertStatus;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.product.ProductRepository;
import com.afriland.ticket2cash.ticket.TicketRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/demo-report")
public class DemoReportController {

    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;
    private final TicketRepository ticketRepository;
    private final ClaimRepository claimRepository;
    private final CashbackPaymentRepository paymentRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository userRepository;

    public DemoReportController(MerchantRepository merchantRepository,
                                ProductRepository productRepository,
                                CampaignRepository campaignRepository,
                                TicketRepository ticketRepository,
                                ClaimRepository claimRepository,
                                CashbackPaymentRepository paymentRepository,
                                FraudAlertRepository fraudAlertRepository,
                                AuditLogRepository auditLogRepository,
                                AppUserRepository userRepository) {
        this.merchantRepository = merchantRepository;
        this.productRepository = productRepository;
        this.campaignRepository = campaignRepository;
        this.ticketRepository = ticketRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(HttpServletRequest request) {

        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        List<CashbackPayment> payments = paymentRepository.findAll();
        List<AuditLog> logs = auditLogRepository.findAll();
        List<AppUser> users = userRepository.findAll();

        BigDecimal totalCashbackPaid = payments.stream()
                .filter(p -> p.getStatus() == CashbackPaymentStatus.SUCCESS)
                .map(CashbackPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeUsers = users.stream()
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .count();

        long lockedUsers = users.stream()
                .filter(u -> Boolean.TRUE.equals(u.getAccountLocked()))
                .count();

        long activeAdmins = users.stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .filter(u -> !Boolean.TRUE.equals(u.getAccountLocked()))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("projectName", "Ticket2Cash - Plateforme cashback co-brandee");
        response.put("organization", "Afriland First Bank");
        response.put("generatedAt", LocalDateTime.now());
        response.put("generatedBy", getCurrentUsername(request));

        response.put("businessObjective",
                "Digitaliser le processus de cashback apres achat en supermarche via ticket OCR, claim, validation, paiement cashback et controle anti-fraude.");

        response.put("valueProposition",
                "La solution permet de suivre les campagnes cashback, automatiser les demandes clients, controler les doublons, journaliser les actions sensibles et fournir un pilotage metier en temps reel.");

        response.put("modules", buildModules());

        response.put("totalMerchants", merchantRepository.count());
        response.put("totalProducts", productRepository.count());
        response.put("totalCampaigns", campaignRepository.count());
        response.put("activeCampaigns", campaignRepository.findAll().stream()
                .filter(c -> c.getStatus() == CampaignStatus.ACTIVE)
                .count());
        response.put("totalTickets", ticketRepository.count());
        response.put("totalClaims", claimRepository.count());
        response.put("approvedClaims", claimRepository.findAll().stream()
                .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
                .count());
        response.put("paidClaims", claimRepository.findAll().stream()
                .filter(c -> c.getStatus() == ClaimStatus.PAID)
                .count());
        response.put("totalPayments", paymentRepository.count());
        response.put("totalCashbackPaid", totalCashbackPaid);
        response.put("totalFraudAlerts", fraudAlertRepository.count());
        response.put("openFraudAlerts", fraudAlertRepository.findAll().stream()
                .filter(a -> a.getStatus() == FraudAlertStatus.OPEN)
                .count());

        response.put("totalUsers", users.size());
        response.put("activeUsers", activeUsers);
        response.put("lockedUsers", lockedUsers);
        response.put("activeAdmins", activeAdmins);
        response.put("loginSuccess", countAction(logs, "LOGIN_SUCCESS"));
        response.put("loginFailed", countAction(logs, "LOGIN_FAILED"));
        response.put("adminUnlocks", countAction(logs, "ADMIN_UNLOCK_USER"));
        response.put("adminPasswordResets", countAction(logs, "ADMIN_RESET_USER_PASSWORD"));

        response.put("lastDemoFlowLogs", buildRecentLogs(logs, "DEMO_FLOW", 10));
        response.put("recentImportantLogs", buildRecentImportantLogs(logs, 20));
        response.put("securityHighlights", buildSecurityHighlights());
        response.put("fraudHighlights", buildFraudHighlights());

        return ResponseEntity.ok(response);
    }

    private List<Map<String, Object>> buildModules() {
        return List.of(
                module("Authentification et roles", "ADMIN, OPERATEUR, LECTEUR avec restrictions par role"),
                module("Gestion utilisateurs", "Creation, activation, desactivation, changement de role"),
                module("Securite compte", "Verrouillage apres echecs, deblocage par ADMIN, reset password"),
                module("OCR ticket simule", "Creation automatique de ticket et extraction de donnees"),
                module("Claims cashback", "Soumission, approbation, rejet et paiement"),
                module("Paiements cashback", "Batch de paiement et statut SUCCESS/FAILED"),
                module("Anti-fraude", "Detection doublon ticket et alertes fraude"),
                module("Audit logs", "Journalisation des actions sensibles"),
                module("Dashboards", "Dashboard metier, dashboard securite, rapport demo"),
                module("Exports CSV", "Exports claims, paiements, alertes et logs")
        );
    }

    private Map<String, Object> module(String name, String description) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("module", name);
        map.put("description", description);
        return map;
    }

    private long countAction(List<AuditLog> logs, String action) {
        return logs.stream()
                .filter(log -> Objects.equals(action, log.getAction()))
                .count();
    }

    private List<Map<String, Object>> buildRecentLogs(List<AuditLog> logs, String moduleName, int limit) {
        return logs.stream()
                .filter(log -> Objects.equals(moduleName, log.getModuleName()))
                .sorted(logComparator())
                .limit(limit)
                .map(this::toLogMap)
                .toList();
    }

    private List<Map<String, Object>> buildRecentImportantLogs(List<AuditLog> logs, int limit) {
        return logs.stream()
                .filter(this::isImportantLog)
                .sorted(logComparator())
                .limit(limit)
                .map(this::toLogMap)
                .toList();
    }

    private boolean isImportantLog(AuditLog log) {
        if (log.getAction() == null) {
            return false;
        }

        String action = log.getAction();

        return action.startsWith("LOGIN")
                || action.contains("PASSWORD")
                || action.contains("USER")
                || action.contains("FRAUD")
                || action.contains("CASHBACK")
                || action.contains("CLAIM")
                || action.contains("DEMO_FLOW")
                || action.contains("EXPORT");
    }

    private Comparator<AuditLog> logComparator() {
        return (a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        };
    }

    private Map<String, Object> toLogMap(AuditLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId());
        map.put("action", log.getAction());
        map.put("module", log.getModuleName());
        map.put("entityType", log.getEntityType());
        map.put("entityId", log.getEntityId());
        map.put("actor", log.getActor());
        map.put("status", log.getStatus());
        map.put("message", log.getMessage());
        map.put("createdAt", log.getCreatedAt());
        return map;
    }

    private List<Map<String, Object>> buildSecurityHighlights() {
        return List.of(
                item("Authentification", "Connexion par session avec roles applicatifs"),
                item("Controle acces", "ADMIN dispose des droits complets, OPERATEUR execute les traitements, LECTEUR consulte uniquement"),
                item("Anti-blocage ADMIN", "Un ADMIN ne peut pas retirer son propre role ou se desactiver lui-meme"),
                item("Verrouillage compte", "Compte verrouille apres plusieurs mauvais mots de passe"),
                item("Audit", "Toutes les actions sensibles sont journalisees")
        );
    }

    private List<Map<String, Object>> buildFraudHighlights() {
        return List.of(
                item("Doublon ticket", "Controle du hash ticket pour eviter la reutilisation d'un meme ticket"),
                item("Score risque", "Score normal ou eleve selon le contexte de detection"),
                item("Alertes fraude", "Creation d'alertes OPEN, UNDER_REVIEW, RESOLVED ou REJECTED"),
                item("Traçabilite", "Les traitements de fraude sont relies aux tickets, claims et utilisateurs")
        );
    }

    private Map<String, Object> item(String title, String description) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("point", title);
        map.put("description", description);
        return map;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        return session != null
                && "ADMIN".equals(String.valueOf(session.getAttribute("AUTH_ROLE")));
    }

    private String getCurrentUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("AUTH_USERNAME") == null) {
            return "UNKNOWN";
        }

        return String.valueOf(session.getAttribute("AUTH_USERNAME"));
    }
}