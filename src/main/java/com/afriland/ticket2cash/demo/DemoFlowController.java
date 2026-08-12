package com.afriland.ticket2cash.demo;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.cashback.CashbackPaymentStatus;
import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.merchant.MerchantStatus;
import com.afriland.ticket2cash.product.CashbackType;
import com.afriland.ticket2cash.ticket.Ticket;
import com.afriland.ticket2cash.ticket.TicketRepository;
import com.afriland.ticket2cash.ticket.TicketStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/demo-flow")
public class DemoFlowController {

    private final MerchantRepository merchantRepository;
    private final CampaignRepository campaignRepository;
    private final TicketRepository ticketRepository;
    private final ClaimRepository claimRepository;
    private final CashbackPaymentRepository paymentRepository;
    private final AuditLogService auditLogService;

    public DemoFlowController(MerchantRepository merchantRepository,
                              CampaignRepository campaignRepository,
                              TicketRepository ticketRepository,
                              ClaimRepository claimRepository,
                              CashbackPaymentRepository paymentRepository,
                              AuditLogService auditLogService) {
        this.merchantRepository = merchantRepository;
        this.campaignRepository = campaignRepository;
        this.ticketRepository = ticketRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runFullDemo(HttpServletRequest request) {

        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        String actor = getCurrentUsername(request);
        String stamp = String.valueOf(System.currentTimeMillis());

        Merchant merchant = getOrCreateDemoMerchant();
        Campaign campaign = getOrCreateDemoCampaign(merchant);

        BigDecimal ticketAmount = new BigDecimal("44500");
        BigDecimal cashbackAmount = new BigDecimal("2225");

        String ticketNumber = "FLOW-TCK-" + stamp;
        String claimReference = "FLOW-CL-" + stamp;
        String paymentReference = "FLOW-PAY-" + stamp;
        String userId = "CLIENT-FLOW-001";

        Ticket ticket = new Ticket();
        ticket.setMerchantId(merchant.getId());
        ticket.setTicketNumber(ticketNumber);
        ticket.setStoreName(merchant.getName());
        ticket.setTicketDateTime(LocalDateTime.now());
        ticket.setTotalAmount(ticketAmount);
        ticket.setCurrency("FCFA");
        ticket.setTicketHash(sha256(merchant.getId() + "|" + ticketNumber + "|" + ticketAmount));
        ticket.setFraudScore(20);
        ticket.setStatus(TicketStatus.OCR_PROCESSED);
        ticket.setOcrRawText("OCR FLOW DEMO - TICKET " + ticketNumber + " - TOTAL " + ticketAmount + " FCFA");
        ticket = ticketRepository.save(ticket);

        auditLogService.log(
                "DEMO_FLOW_TICKET_CREATED",
                "DEMO_FLOW",
                "Ticket",
                ticket.getId(),
                actor,
                "SUCCESS",
                "Demo flow ticket OCR created"
        );

        Claim claim = new Claim();
        claim.setClaimReference(claimReference);
        claim.setUserId(userId);
        claim.setMerchantId(merchant.getId());
        claim.setCampaignId(campaign.getId());
        claim.setTicketId(ticket.getId());
        claim.setTicketAmount(ticketAmount);
        claim.setCashbackAmount(cashbackAmount);
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setSubmittedAt(LocalDateTime.now());
        claim = claimRepository.save(claim);

        auditLogService.log(
                "DEMO_FLOW_CLAIM_SUBMITTED",
                "DEMO_FLOW",
                "Claim",
                claim.getId(),
                actor,
                "SUCCESS",
                "Demo flow claim submitted"
        );

        claim.setStatus(ClaimStatus.APPROVED);
        claim = claimRepository.save(claim);

        auditLogService.log(
                "DEMO_FLOW_CLAIM_APPROVED",
                "DEMO_FLOW",
                "Claim",
                claim.getId(),
                actor,
                "SUCCESS",
                "Demo flow claim approved"
        );

        CashbackPayment payment = new CashbackPayment();
        payment.setPaymentReference(paymentReference);
        payment.setClaimId(claim.getId());
        payment.setMerchantId(merchant.getId());
        payment.setCampaignId(campaign.getId());
        payment.setUserId(userId);
        payment.setAmount(cashbackAmount);
        payment.setCurrency("FCFA");
        payment.setStatus(CashbackPaymentStatus.SUCCESS);
        payment.setProcessedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        claim.setStatus(ClaimStatus.PAID);
        claim = claimRepository.save(claim);

        auditLogService.log(
                "DEMO_FLOW_PAYMENT_SUCCESS",
                "DEMO_FLOW",
                "CashbackPayment",
                payment.getId(),
                actor,
                "SUCCESS",
                "Demo flow cashback payment success"
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Parcours demo execute avec succes");
        response.put("merchantId", merchant.getId());
        response.put("merchantName", merchant.getName());
        response.put("campaignId", campaign.getId());
        response.put("campaignName", campaign.getName());
        response.put("ticketId", ticket.getId());
        response.put("ticketNumber", ticket.getTicketNumber());
        response.put("ticketAmount", ticket.getTotalAmount());
        response.put("claimId", claim.getId());
        response.put("claimReference", claim.getClaimReference());
        response.put("claimStatus", claim.getStatus());
        response.put("cashbackAmount", claim.getCashbackAmount());
        response.put("paymentId", payment.getId());
        response.put("paymentReference", payment.getPaymentReference());
        response.put("paymentStatus", payment.getStatus());
        response.put("currency", payment.getCurrency());
        response.put("executedBy", actor);
        response.put("executedAt", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    private Merchant getOrCreateDemoMerchant() {
        return merchantRepository.findAll()
                .stream()
                .filter(m -> "DEMO-FLOW-T2C".equals(m.getNiu()))
                .findFirst()
                .orElseGet(() -> {
                    Merchant merchant = new Merchant();
                    merchant.setName("Supermarche Demo Flow");
                    merchant.setBrandName("Demo Flow Market");
                    merchant.setRccm("RCCM-DEMO-FLOW-T2C");
                    merchant.setNiu("DEMO-FLOW-T2C");
                    merchant.setPhone("690000001");
                    merchant.setEmail("demo.flow@ticket2cash.local");
                    merchant.setCity("Yaounde");
                    merchant.setAddress("Agence demo Ticket2Cash");
                    merchant.setStatus(MerchantStatus.ACTIVE);
                    return merchantRepository.save(merchant);
                });
    }

    private Campaign getOrCreateDemoCampaign(Merchant merchant) {
        return campaignRepository.findAll()
                .stream()
                .filter(c -> "Campagne Demo Flow Cashback".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Campaign campaign = new Campaign();
                    campaign.setMerchant(merchant);
                    campaign.setName("Campagne Demo Flow Cashback");
                    campaign.setDescription("Campagne utilisee pour le parcours complet de demonstration");
                    campaign.setStartDate(LocalDate.now().minusDays(1));
                    campaign.setEndDate(LocalDate.now().plusDays(30));
                    campaign.setCashbackType(CashbackType.PERCENTAGE);
                    campaign.setCashbackValue(new BigDecimal("5"));
                    campaign.setDailyLimitPerClient(new BigDecimal("2"));
                    campaign.setMonthlyLimitPerClient(new BigDecimal("10"));
                    campaign.setTotalBudget(new BigDecimal("1000000"));
                    campaign.setStatus(CampaignStatus.ACTIVE);
                    return campaignRepository.save(campaign);
                });
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            return "HASH-FALLBACK-" + System.currentTimeMillis();
        }
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