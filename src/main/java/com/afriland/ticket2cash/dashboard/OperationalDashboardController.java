package com.afriland.ticket2cash.dashboard;

import com.afriland.ticket2cash.audit.AuditLog;
import com.afriland.ticket2cash.audit.AuditLogRepository;
import com.afriland.ticket2cash.cashback.CashbackCreditStatus;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.cashback.CashbackPaymentStatus;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.fraud.FraudAlert;
import com.afriland.ticket2cash.fraud.FraudAlertRepository;
import com.afriland.ticket2cash.loyalty.LoyaltyClient;
import com.afriland.ticket2cash.loyalty.LoyaltyClientRepository;
import com.afriland.ticket2cash.pos.PosTransaction;
import com.afriland.ticket2cash.pos.PosTransactionRepository;
import com.afriland.ticket2cash.pos.TransactionWorkflowStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class OperationalDashboardController {
    private final PosTransactionRepository transactions;
    private final CashbackPaymentRepository payments;
    private final ClaimRepository claims;
    private final LoyaltyClientRepository loyaltyClients;
    private final FraudAlertRepository fraudAlerts;
    private final AuditLogRepository auditLogs;

    public OperationalDashboardController(PosTransactionRepository transactions, CashbackPaymentRepository payments,
                                          ClaimRepository claims, LoyaltyClientRepository loyaltyClients,
                                          FraudAlertRepository fraudAlerts, AuditLogRepository auditLogs) {
        this.transactions = transactions; this.payments = payments; this.claims = claims;
        this.loyaltyClients = loyaltyClients; this.fraudAlerts = fraudAlerts; this.auditLogs = auditLogs;
    }

    @GetMapping("/operational-summary")
    public ResponseEntity<?> summary(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        String role = session == null ? null : String.valueOf(session.getAttribute("AUTH_ROLE"));
        if (!("ADMIN".equalsIgnoreCase(role) || "SUPERVISEUR".equalsIgnoreCase(role)))
            return ResponseEntity.status(403).body(Map.of("error", "ADMIN or SUPERVISEUR role required"));

        List<PosTransaction> txs = transactions.findAllByOrderByReceivedAtDesc();
        List<CashbackPayment> pays = payments.findAll();
        List<Claim> cls = claims.findAll();
        List<LoyaltyClient> clients = loyaltyClients.findAll();
        List<FraudAlert> alerts = fraudAlerts.findAll();

        BigDecimal txAmount = sum(txs, PosTransaction::getAmount);
        BigDecimal campaign = sum(pays, CashbackPayment::getCampaignCashbackAmount);
        BigDecimal bonus = sum(pays, CashbackPayment::getLoyaltyBonusAmount);
        BigDecimal finalCashback = pays.stream().map(p -> p.getFinalCashbackAmount() != null ? p.getFinalCashbackAmount() : p.getAmount()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long revision = txs.stream().filter(t -> t.isManualReviewRequired() || status(t) == TransactionWorkflowStatus.MANUAL_REVIEW).count();
        long rejected = txs.stream().filter(t -> status(t) == TransactionWorkflowStatus.REJECTED).count();
        long ready = txs.stream().filter(t -> status(t) == TransactionWorkflowStatus.APPROVED_FOR_PAYMENT).count();
        long pendingPay = pays.stream().filter(p -> p.getStatus() == CashbackPaymentStatus.PENDING).count();
        long processedPay = pays.stream().filter(p -> p.getStatus() == CashbackPaymentStatus.SUCCESS).count();
        long failedPay = pays.stream().filter(p -> p.getStatus() == CashbackPaymentStatus.FAILED).count();
        long pendingCredit = pays.stream().filter(p -> p.getCreditStatus() == CashbackCreditStatus.CREDIT_PENDING).count();
        long credited = pays.stream().filter(p -> p.getCreditStatus() == CashbackCreditStatus.CREDITED).count();
        long failedCredit = pays.stream().filter(p -> p.getCreditStatus() == CashbackCreditStatus.FAILED).count();
        long openClaims = cls.stream().filter(c -> c.getStatus() == ClaimStatus.SUBMITTED || c.getStatus() == ClaimStatus.PAYMENT_FAILED).count();
        long highRisk = alerts.stream().filter(a -> a.getRiskScore() != null && a.getRiskScore() >= 70).count();

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("totalTransactionsCartes", txs.size()); out.put("montantTotalTransactions", txAmount);
        out.put("cashbackCalcule", finalCashback); out.put("paiementsTraites", processedPay); out.put("creditsClientsEffectues", credited); out.put("controlesEnAttente", revision); out.put("reclamationsOuvertes", openClaims);
        out.put("transactionsImportees", txs.size()); out.put("transactionsEligibles", pays.stream().filter(p -> p.getStatus() != CashbackPaymentStatus.FAILED).count());
        out.put("transactionsNonEligibles", pays.stream().filter(p -> p.getStatus() == CashbackPaymentStatus.FAILED).count()); out.put("transactionsEnRevision", revision); out.put("transactionsRejetees", rejected); out.put("transactionsPretesPaiement", ready);
        out.put("paiementsGeneres", pays.size()); out.put("paiementsEnAttente", pendingPay); out.put("paiementsTraites", processedPay); out.put("paiementsEchec", failedPay);
        out.put("creditsEnAttente", pendingCredit); out.put("creditsEffectues", credited); out.put("creditsEchec", failedCredit); out.put("montantCredite", pays.stream().filter(p -> p.getCreditStatus() == CashbackCreditStatus.CREDITED).map(CashbackPayment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        out.put("cashbackCampagne", campaign); out.put("bonusFidelite", bonus); out.put("cashbackFinal", finalCashback); out.put("cashbackEnAttentePaiement", pays.stream().filter(p -> p.getStatus() == CashbackPaymentStatus.PENDING).map(this::cashbackAmount).reduce(BigDecimal.ZERO, BigDecimal::add)); out.put("cashbackTraite", pays.stream().filter(p -> p.getStatus() == CashbackPaymentStatus.SUCCESS).map(this::cashbackAmount).reduce(BigDecimal.ZERO, BigDecimal::add)); out.put("cashbackCredite", pays.stream().filter(p -> p.getCreditStatus() == CashbackCreditStatus.CREDITED).map(this::cashbackAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        out.put("clientsFideliteActifs", clients.size()); out.put("volumeFideliteCumule", sum(clients, LoyaltyClient::getLifetimeVolume)); out.put("bonusFideliteAppliques", sum(clients, LoyaltyClient::getLifetimeCashback));
        Map<String,Long> tiers = new LinkedHashMap<>(); for(String k: List.of("Essentiel","Premium","Prestige","Elite","Non attribué")) tiers.put(k,0L); clients.forEach(c -> tiers.compute(tier(c.getTier()), (k,v)->v+1)); out.put("repartitionNiveaux", tiers);
        out.put("risquesEleves", highRisk); out.put("paiementsEnEchec", failedPay); out.put("creditsEnEchec", failedCredit);
        out.put("dernieresTransactions", txs.stream().limit(5).map(this::txDto).toList()); out.put("derniersPaiements", pays.stream().sorted(Comparator.comparing(CashbackPayment::getProcessedAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(5).map(this::paymentDto).toList()); out.put("derniersCredits", pays.stream().filter(p -> p.getCreditStatus() == CashbackCreditStatus.CREDITED).sorted(Comparator.comparing(CashbackPayment::getCreditedAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(5).map(this::paymentDto).toList()); out.put("dernieresReclamations", cls.stream().sorted(Comparator.comparing(Claim::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(5).map(this::claimDto).toList()); out.put("dernieresActionsAudit", auditLogs.findAll().stream().sorted(Comparator.comparing(AuditLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(5).map(this::auditDto).toList());
        return ResponseEntity.ok(out);
    }
    private TransactionWorkflowStatus status(PosTransaction t){return t.getWorkflowStatus()==null?TransactionWorkflowStatus.RECEIVED:t.getWorkflowStatus();}
    private BigDecimal cashbackAmount(CashbackPayment p){return p.getFinalCashbackAmount()!=null?p.getFinalCashbackAmount():nvl(p.getAmount());}
    private BigDecimal nvl(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private <T> BigDecimal sum(Collection<T> xs, Function<T,BigDecimal> f){return xs.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private String tier(String t){if(t==null||t.isBlank()||"CLASSIC".equalsIgnoreCase(t))return "Essentiel"; return switch(t.toUpperCase(Locale.ROOT)){case "SILVER"->"Premium";case "GOLD"->"Prestige";case "PLATINUM"->"Elite";default->t;};}
    private Map<String,Object> txDto(PosTransaction t){Map<String,Object> m=new LinkedHashMap<>();m.put("transactionRef",safe(t.getTransactionRef()));m.put("maskedCard",safe(t.getMaskedCard()));m.put("amount",nvl(t.getAmount()));m.put("merchantName",safe(t.getMerchantName()));m.put("workflowStatus",status(t));return m;}
    private Map<String,Object> paymentDto(CashbackPayment p){Map<String,Object> m=new LinkedHashMap<>();m.put("paymentReference",safe(p.getPaymentReference()));m.put("transactionRef",safe(p.getTransactionRef()));m.put("amount",nvl(p.getAmount()));m.put("status",p.getStatus()==null?"Non renseigné":p.getStatus());m.put("creditStatus",p.getCreditStatus()==null?"Non disponible":p.getCreditStatus());return m;}
    private Map<String,Object> claimDto(Claim c){Map<String,Object> m=new LinkedHashMap<>();m.put("claimReference",safe(c.getClaimReference()));m.put("cashbackAmount",nvl(c.getCashbackAmount()));m.put("status",c.getStatus()==null?"Non renseigné":c.getStatus());m.put("submittedAt",String.valueOf(c.getSubmittedAt()));return m;}
    private Map<String,Object> auditDto(AuditLog a){Map<String,Object> m=new LinkedHashMap<>();m.put("action",safe(a.getAction()));m.put("actor",safe(a.getActor()));m.put("status",safe(a.getStatus()));m.put("createdAt",String.valueOf(a.getCreatedAt()));return m;}
    private String safe(String s){return s==null?"Non renseigné":s;}
}
