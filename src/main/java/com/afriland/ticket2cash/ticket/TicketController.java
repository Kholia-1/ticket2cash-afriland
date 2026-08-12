package com.afriland.ticket2cash.ticket;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.fraud.FraudAlert;
import com.afriland.ticket2cash.fraud.FraudAlertRepository;
import com.afriland.ticket2cash.fraud.FraudAlertStatus;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.product.CashbackType;
import com.afriland.ticket2cash.settings.SystemSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;
    private final ClaimRepository claimRepository;
    private final MerchantRepository merchantRepository;
    private final CampaignRepository campaignRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final AuditLogService auditLogService;
    private final SystemSettingService settingService;

    public TicketController(TicketRepository ticketRepository,
                            ClaimRepository claimRepository,
                            MerchantRepository merchantRepository,
                            CampaignRepository campaignRepository,
                            FraudAlertRepository fraudAlertRepository,
                            AuditLogService auditLogService,
                            SystemSettingService settingService) {
        this.ticketRepository = ticketRepository;
        this.claimRepository = claimRepository;
        this.merchantRepository = merchantRepository;
        this.campaignRepository = campaignRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.auditLogService = auditLogService;
        this.settingService = settingService;
    }

    @GetMapping
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    @PostMapping("/simulate-ocr")
    public ResponseEntity<?> simulateOcrAndCreateClaim(@RequestBody SimulateOcrRequest request) {

        String actor = settingService.getString("DEFAULT_ACTOR", "ADMIN_DEMO");
        String currency = settingService.getString("DEFAULT_CURRENCY", "FCFA");
        BigDecimal ticketAmount = settingService.getBigDecimal("TICKET_SIMULATED_AMOUNT", new BigDecimal("44500"));
        int normalScore = settingService.getInteger("FRAUD_NORMAL_SCORE", 20);
        int duplicateScore = settingService.getInteger("FRAUD_DUPLICATE_SCORE", 95);
        boolean antiFraudEnabled = settingService.getBoolean("ANTI_FRAUD_ENABLED", true);

        Merchant merchant = merchantRepository.findById(request.getMerchantId()).orElse(null);

        if (merchant == null) {
            auditLogService.log(
                    "SIMULATE_OCR_FAILED",
                    "OCR",
                    "Ticket",
                    null,
                    request.getUserId(),
                    "FAILED",
                    "Merchant not found: " + request.getMerchantId()
            );

            return ResponseEntity.badRequest().body("Commercant introuvable avec id = " + request.getMerchantId());
        }

        Campaign campaign = campaignRepository.findById(request.getCampaignId()).orElse(null);

        if (campaign == null) {
            auditLogService.log(
                    "SIMULATE_OCR_FAILED",
                    "OCR",
                    "Ticket",
                    null,
                    request.getUserId(),
                    "FAILED",
                    "Campaign not found: " + request.getCampaignId()
            );

            return ResponseEntity.badRequest().body("Campagne introuvable avec id = " + request.getCampaignId());
        }

        String ticketNumber = request.getTicketNumber();

        if (ticketNumber == null || ticketNumber.isBlank()) {
            ticketNumber = "TCK-" + System.currentTimeMillis();
        }

        String ticketHash = sha256(merchant.getId() + "|" + ticketNumber + "|" + ticketAmount);

        if (antiFraudEnabled && ticketRepository.existsByTicketHash(ticketHash)) {
            FraudAlert alert = new FraudAlert();
            alert.setAlertReference("FRAUD-" + System.currentTimeMillis());
            alert.setMerchantId(merchant.getId());
            alert.setUserId(request.getUserId());
            alert.setRiskScore(duplicateScore);
            alert.setReason("DUPLICATE_TICKET");
            alert.setStatus(FraudAlertStatus.OPEN);

            FraudAlert savedAlert = fraudAlertRepository.save(alert);

            auditLogService.log(
                    "DUPLICATE_TICKET_DETECTED",
                    "FRAUD",
                    "FraudAlert",
                    savedAlert.getId(),
                    request.getUserId(),
                    "FAILED",
                    "Duplicate ticket detected: " + ticketNumber
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Ticket rejete : doublon detecte.");
            response.put("antiFraudEnabled", true);
            response.put("fraudScore", duplicateScore);
            response.put("reason", "DUPLICATE_TICKET");
            response.put("alert", savedAlert);

            return ResponseEntity.badRequest().body(response);
        }

        Ticket ticket = new Ticket();
        ticket.setMerchantId(merchant.getId());
        ticket.setTicketNumber(ticketNumber);
        ticket.setStoreName(merchant.getBrandName());
        ticket.setTicketDateTime(LocalDateTime.now());
        ticket.setTotalAmount(ticketAmount);
        ticket.setCurrency(currency);
        ticket.setTicketHash(ticketHash);
        ticket.setFraudScore(normalScore);
        ticket.setStatus(TicketStatus.OCR_PROCESSED);
        ticket.setOcrRawText("OCR SIMULE : RIZ PARF 5KG 8500; HUILE 5L 12000; LAIT 6PCS 9000; SUCRE 5KG 15000; TOTAL " + ticketAmount + " " + currency);

        Ticket savedTicket = ticketRepository.save(ticket);

        BigDecimal cashbackAmount = calculateCashback(ticketAmount, campaign);

        Claim claim = new Claim();
        claim.setClaimReference("CLM-" + System.currentTimeMillis());
        claim.setUserId(request.getUserId());
        claim.setMerchantId(merchant.getId());
        claim.setCampaignId(campaign.getId());
        claim.setTicketId(savedTicket.getId());
        claim.setTicketAmount(ticketAmount);
        claim.setCashbackAmount(cashbackAmount);
        claim.setStatus(ClaimStatus.SUBMITTED);

        Claim savedClaim = claimRepository.save(claim);

        auditLogService.log(
                "SIMULATE_OCR_SUCCESS",
                "OCR",
                "Ticket",
                savedTicket.getId(),
                actor,
                "SUCCESS",
                "Ticket created and claim created. Claim ID=" + savedClaim.getId()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "OCR simule avec succes. Ticket et claim crees.");
        response.put("antiFraudEnabled", antiFraudEnabled);
        response.put("ticketAmountFromSettings", ticketAmount);
        response.put("currencyFromSettings", currency);
        response.put("fraudScore", normalScore);
        response.put("fraudDecision", "APPROVED_FOR_CLAIM");
        response.put("ticket", savedTicket);
        response.put("claim", savedClaim);

        return ResponseEntity.ok(response);
    }

    private BigDecimal calculateCashback(BigDecimal ticketAmount, Campaign campaign) {

        if (campaign.getCashbackType() == CashbackType.PERCENTAGE) {
            return ticketAmount
                    .multiply(campaign.getCashbackValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        if (campaign.getCashbackType() == CashbackType.FIXED_AMOUNT) {
            return campaign.getCashbackValue();
        }

        return BigDecimal.ZERO;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedHash);
        } catch (Exception e) {
            throw new RuntimeException("Erreur calcul hash SHA-256", e);
        }
    }
}