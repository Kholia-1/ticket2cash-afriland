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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/demo-report")
public class DemoReportExportController {

    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;
    private final TicketRepository ticketRepository;
    private final ClaimRepository claimRepository;
    private final CashbackPaymentRepository paymentRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository userRepository;

    public DemoReportExportController(MerchantRepository merchantRepository,
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

    @GetMapping(value = "/export-html", produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> exportHtml(HttpServletRequest request) {

        if (!isAdmin(request)) {
            return ResponseEntity.status(403)
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body("<h1>ADMIN role required</h1>");
        }

        String html = buildHtmlReport(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=ticket2cash-rapport-demo.html")
                .body(html);
    }

    private String buildHtmlReport(HttpServletRequest request) {
        List<CashbackPayment> payments = paymentRepository.findAll();
        List<AuditLog> logs = auditLogRepository.findAll();
        List<AppUser> users = userRepository.findAll();

        BigDecimal totalCashbackPaid = payments.stream()
                .filter(p -> p.getStatus() == CashbackPaymentStatus.SUCCESS)
                .map(CashbackPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeCampaigns = campaignRepository.findAll().stream()
                .filter(c -> c.getStatus() == CampaignStatus.ACTIVE)
                .count();

        long approvedClaims = claimRepository.findAll().stream()
                .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
                .count();

        long paidClaims = claimRepository.findAll().stream()
                .filter(c -> c.getStatus() == ClaimStatus.PAID)
                .count();

        long openFraudAlerts = fraudAlertRepository.findAll().stream()
                .filter(a -> a.getStatus() == FraudAlertStatus.OPEN)
                .count();

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

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html lang='fr'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>Rapport Demo Ticket2Cash</title>");
        html.append("<style>");
        html.append("body{font-family:Arial,Helvetica,sans-serif;margin:35px;color:#222;line-height:1.45;}");
        html.append("h1{color:#0b3d2e;margin-bottom:5px;}");
        html.append("h2{color:#0b3d2e;border-bottom:2px solid #0b3d2e;padding-bottom:6px;margin-top:28px;}");
        html.append(".subtitle{color:#555;margin-top:0;}");
        html.append(".box{border:1px solid #ddd;border-radius:8px;padding:14px;margin:12px 0;background:#fafafa;}");
        html.append(".grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;}");
        html.append(".kpi{border:1px solid #ddd;border-radius:8px;padding:12px;background:#fff;}");
        html.append(".kpi .label{font-size:12px;color:#666;text-transform:uppercase;}");
        html.append(".kpi .value{font-size:24px;font-weight:bold;color:#0b3d2e;margin-top:6px;}");
        html.append("table{width:100%;border-collapse:collapse;margin-top:10px;font-size:13px;}");
        html.append("th,td{border:1px solid #ddd;padding:8px;text-align:left;vertical-align:top;}");
        html.append("th{background:#0b3d2e;color:white;}");
        html.append(".footer{margin-top:35px;font-size:12px;color:#666;border-top:1px solid #ddd;padding-top:12px;}");
        html.append(".print-btn{position:fixed;right:25px;top:20px;background:#0b3d2e;color:white;border:none;padding:10px 16px;border-radius:6px;cursor:pointer;}");
        html.append("@media print{.print-btn{display:none;} body{margin:15px;} .grid{grid-template-columns:repeat(2,1fr);} }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");

        html.append("<button class='print-btn' onclick='window.print()'>Imprimer / PDF</button>");

        html.append("<h1>Rapport de demonstration Ticket2Cash</h1>");
        html.append("<p class='subtitle'>Plateforme cashback co-brandee - Afriland First Bank</p>");

        html.append("<div class='box'>");
        html.append("<strong>Genere le :</strong> ").append(escape(String.valueOf(LocalDateTime.now()))).append("<br>");
        html.append("<strong>Genere par :</strong> ").append(escape(getCurrentUsername(request))).append("<br>");
        html.append("<strong>Objectif metier :</strong> Digitaliser le processus de cashback apres achat en supermarche via ticket OCR, claim, validation, paiement cashback et controle anti-fraude.");
        html.append("</div>");

        html.append("<h2>1. Synthese metier</h2>");
        html.append("<div class='grid'>");
        kpi(html, "Commercants", merchantRepository.count());
        kpi(html, "Produits", productRepository.count());
        kpi(html, "Campagnes actives", activeCampaigns);
        kpi(html, "Tickets OCR", ticketRepository.count());
        kpi(html, "Claims totales", claimRepository.count());
        kpi(html, "Claims approuvees", approvedClaims);
        kpi(html, "Claims payees", paidClaims);
        kpi(html, "Cashback paye", totalCashbackPaid + " FCFA");
        kpi(html, "Paiements", paymentRepository.count());
        kpi(html, "Alertes fraude", fraudAlertRepository.count());
        kpi(html, "Alertes ouvertes", openFraudAlerts);
        kpi(html, "Utilisateurs", users.size());
        html.append("</div>");

        html.append("<h2>2. Synthese securite</h2>");
        html.append("<div class='grid'>");
        kpi(html, "Utilisateurs actifs", activeUsers);
        kpi(html, "Comptes verrouilles", lockedUsers);
        kpi(html, "Admins actifs", activeAdmins);
        kpi(html, "Connexions OK", countAction(logs, "LOGIN_SUCCESS"));
        kpi(html, "Echecs connexion", countAction(logs, "LOGIN_FAILED"));
        kpi(html, "Deblocages ADMIN", countAction(logs, "ADMIN_UNLOCK_USER"));
        kpi(html, "Reset password ADMIN", countAction(logs, "ADMIN_RESET_USER_PASSWORD"));
        kpi(html, "Verrouillages", countAction(logs, "LOGIN_ACCOUNT_LOCKED"));
        html.append("</div>");

        html.append("<h2>3. Modules disponibles</h2>");
        html.append("<table>");
        html.append("<tr><th>Module</th><th>Description</th></tr>");
        row(html, "Authentification et roles", "ADMIN, OPERATEUR, LECTEUR avec restrictions par role.");
        row(html, "Gestion utilisateurs", "Creation, activation, desactivation, changement de role et reset mot de passe.");
        row(html, "Securite compte", "Verrouillage apres echecs, deblocage par ADMIN et protection anti-blocage ADMIN.");
        row(html, "OCR ticket simule", "Simulation de lecture ticket et generation de claim.");
        row(html, "Claims cashback", "Soumission, approbation, rejet et paiement.");
        row(html, "Anti-fraude", "Detection de doublon ticket, score risque et alertes fraude.");
        row(html, "Audit logs", "Journalisation des actions sensibles.");
        row(html, "Dashboards", "Dashboard metier, dashboard securite et rapport demo.");
        row(html, "Exports CSV", "Exports claims, paiements, fraudes et logs.");
        html.append("</table>");

        html.append("<h2>4. Valeur ajoutee pour Afriland First Bank</h2>");
        html.append("<div class='box'>");
        html.append("<ul>");
        html.append("<li>Automatisation du traitement des demandes cashback.</li>");
        html.append("<li>Meilleure tracabilite des operations sensibles.</li>");
        html.append("<li>Controle anti-fraude sur tickets et claims.</li>");
        html.append("<li>Pilotage metier en temps reel via dashboards.</li>");
        html.append("<li>Administration securisee des utilisateurs et des roles.</li>");
        html.append("<li>Base evolutive pour integration API avec les systemes bancaires.</li>");
        html.append("</ul>");
        html.append("</div>");

        html.append("<h2>5. Derniers logs du parcours demo</h2>");
        appendLogsTable(html, logs, "DEMO_FLOW", 10);

        html.append("<h2>6. Derniers logs importants</h2>");
        appendImportantLogsTable(html, logs, 20);

        html.append("<div class='footer'>");
        html.append("Rapport genere automatiquement par Ticket2Cash. ");
        html.append("Document de demonstration interne - Afriland First Bank.");
        html.append("</div>");

        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    private void kpi(StringBuilder html, String label, Object value) {
        html.append("<div class='kpi'>");
        html.append("<div class='label'>").append(escape(label)).append("</div>");
        html.append("<div class='value'>").append(escape(String.valueOf(value))).append("</div>");
        html.append("</div>");
    }

    private void row(StringBuilder html, String col1, String col2) {
        html.append("<tr>");
        html.append("<td>").append(escape(col1)).append("</td>");
        html.append("<td>").append(escape(col2)).append("</td>");
        html.append("</tr>");
    }

    private void appendLogsTable(StringBuilder html, List<AuditLog> logs, String moduleName, int limit) {
        html.append("<table>");
        html.append("<tr><th>Date</th><th>Action</th><th>Acteur</th><th>Statut</th><th>Message</th></tr>");

        logs.stream()
                .filter(log -> Objects.equals(moduleName, log.getModuleName()))
                .sorted(logComparator())
                .limit(limit)
                .forEach(log -> logRow(html, log));

        html.append("</table>");
    }

    private void appendImportantLogsTable(StringBuilder html, List<AuditLog> logs, int limit) {
        html.append("<table>");
        html.append("<tr><th>Date</th><th>Module</th><th>Action</th><th>Acteur</th><th>Statut</th><th>Message</th></tr>");

        logs.stream()
                .filter(this::isImportantLog)
                .sorted(logComparator())
                .limit(limit)
                .forEach(log -> {
                    html.append("<tr>");
                    html.append("<td>").append(escape(String.valueOf(log.getCreatedAt()))).append("</td>");
                    html.append("<td>").append(escape(log.getModuleName())).append("</td>");
                    html.append("<td>").append(escape(log.getAction())).append("</td>");
                    html.append("<td>").append(escape(log.getActor())).append("</td>");
                    html.append("<td>").append(escape(log.getStatus())).append("</td>");
                    html.append("<td>").append(escape(log.getMessage())).append("</td>");
                    html.append("</tr>");
                });

        html.append("</table>");
    }

    private void logRow(StringBuilder html, AuditLog log) {
        html.append("<tr>");
        html.append("<td>").append(escape(String.valueOf(log.getCreatedAt()))).append("</td>");
        html.append("<td>").append(escape(log.getAction())).append("</td>");
        html.append("<td>").append(escape(log.getActor())).append("</td>");
        html.append("<td>").append(escape(log.getStatus())).append("</td>");
        html.append("<td>").append(escape(log.getMessage())).append("</td>");
        html.append("</tr>");
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

    private long countAction(List<AuditLog> logs, String action) {
        return logs.stream()
                .filter(log -> Objects.equals(action, log.getAction()))
                .count();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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