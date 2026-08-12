package com.afriland.ticket2cash.mobile;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.fraud.FraudAlert;
import com.afriland.ticket2cash.fraud.FraudAlertRepository;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.pos.TransactionVerificationService;
import com.afriland.ticket2cash.product.CashbackType;
import com.afriland.ticket2cash.ticket.Ticket;
import com.afriland.ticket2cash.ticket.TicketRepository;
import com.afriland.ticket2cash.ticket.TicketStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/mobile")
@CrossOrigin(origins = "*")
public class MobileScanController {

    private final TicketRepository ticketRepository;
    private final ClaimRepository claimRepository;
    private final MerchantRepository merchantRepository;
    private final CampaignRepository campaignRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final MobileClientRepository clientRepository;
    private final AuditLogService auditLogService;
    private final TransactionVerificationService verificationService;

    public MobileScanController(TicketRepository ticketRepository,
                                 ClaimRepository claimRepository,
                                 MerchantRepository merchantRepository,
                                 CampaignRepository campaignRepository,
                                 FraudAlertRepository fraudAlertRepository,
                                 MobileClientRepository clientRepository,
                                 AuditLogService auditLogService,
                                 TransactionVerificationService verificationService) {
        this.ticketRepository = ticketRepository;
        this.claimRepository = claimRepository;
        this.merchantRepository = merchantRepository;
        this.campaignRepository = campaignRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.clientRepository = clientRepository;
        this.auditLogService = auditLogService;
        this.verificationService = verificationService;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> submitScan(@RequestBody Map<String, Object> body,
                                         HttpServletRequest request) {

        Long clientId = MobileAuthController.getMobileClientId(request);
        if (clientId == null) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "NOT_AUTHENTICATED",
                "message", "Please login first"
            ));
        }

        var clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "CLIENT_NOT_FOUND"));
        }

        MobileClient client = clientOpt.get();

        String merchantName = (String) body.get("merchantName");
        Number totalAmountNum = (Number) body.get("totalAmount");
        String ticketNumber = (String) body.get("ticketNumber");
        String ocrText = (String) body.getOrDefault("ocrText", "");

        if (merchantName == null || totalAmountNum == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_FIELDS",
                "message", "merchantName and totalAmount are required"
            ));
        }

        BigDecimal totalAmount = BigDecimal.valueOf(totalAmountNum.doubleValue());

        // Find merchant by name
        Merchant merchant = findMerchantByName(merchantName);
        if (merchant == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MERCHANT_NOT_FOUND",
                "message", "No merchant found with name: " + merchantName
            ));
        }

        // Generate ticket number if not provided
        if (ticketNumber == null || ticketNumber.isBlank()) {
            ticketNumber = "TK-" + System.currentTimeMillis();
        }

        // Generate ticket hash for fraud detection
        String hashInput = merchantName + "|" + ticketNumber + "|" + totalAmount + "|" + client.getPhone();
        String ticketHash = sha256(hashInput);

        // Check for duplicate ticket
        boolean isDuplicate = ticketRepository.existsByTicketHash(ticketHash);
        int fraudScore = isDuplicate ? 90 : 0;

        // Create the ticket
        Ticket ticket = new Ticket();
        ticket.setMerchantId(merchant.getId());
        ticket.setTicketNumber(ticketNumber);
        ticket.setStoreName(merchantName);
        ticket.setTicketDateTime(LocalDateTime.now());
        ticket.setTotalAmount(totalAmount);
        ticket.setCurrency("FCFA");
        ticket.setTicketHash(ticketHash);
        ticket.setFraudScore(fraudScore);
        ticket.setStatus(isDuplicate ? TicketStatus.REJECTED : TicketStatus.OCR_PROCESSED);
        ticket.setOcrRawText(ocrText);

        ticket = ticketRepository.save(ticket);

        auditLogService.log("MOBILE_TICKET_CREATED", "mobile", "Ticket",
            ticket.getId(), client.getPhone(), "SUCCESS",
            "Ticket created from mobile scan: " + ticketNumber + " amount=" + totalAmount + " FCFA");

        // If duplicate, create fraud alert and stop
        if (isDuplicate) {
            FraudAlert alert = new FraudAlert();
            alert.setAlertReference("FRA-" + System.currentTimeMillis());
            alert.setTicketId(ticket.getId());
            alert.setMerchantId(merchant.getId());
            alert.setUserId(client.getPhone());
            alert.setRiskScore(fraudScore);
            alert.setReason("Duplicate ticket detected (hash match)");
            fraudAlertRepository.save(alert);

            auditLogService.log("FRAUD_DETECTED", "mobile", "FraudAlert",
                alert.getId(), client.getPhone(), "WARNING",
                "Duplicate ticket hash detected for ticket " + ticketNumber);

            return ResponseEntity.ok(Map.of(
                "status", "REJECTED",
                "message", "This ticket has already been submitted",
                "ticketId", ticket.getId(),
                "fraudScore", fraudScore
            ));
        }

        // Find active campaign for this merchant
        Campaign campaign = findActiveCampaign(merchant.getId());
        BigDecimal cashbackAmount = BigDecimal.ZERO;
        String campaignName = "No active campaign";
        int cashbackRate = 0;

        if (campaign != null) {
            campaignName = campaign.getName();
            if (campaign.getCashbackType() == CashbackType.PERCENTAGE && campaign.getCashbackValue() != null) {
                cashbackRate = campaign.getCashbackValue().intValue();
                cashbackAmount = totalAmount
                    .multiply(campaign.getCashbackValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            } else if (campaign.getCashbackType() == CashbackType.FIXED_AMOUNT && campaign.getCashbackValue() != null) {
                cashbackAmount = campaign.getCashbackValue();
                cashbackRate = 0;
            }
        }

        // Create the claim — linked to customer's prepaid card
        Claim claim = new Claim();
        claim.setClaimReference("CBX-" + randomRef());
        claim.setUserId(client.getPhone());
        claim.setMerchantId(merchant.getId());
        claim.setCampaignId(campaign != null ? campaign.getId() : null);
        claim.setTicketId(ticket.getId());
        claim.setTicketAmount(totalAmount);
        claim.setCashbackAmount(cashbackAmount);
        claim.setMaskedCard(client.getMaskedCard());
        claim.setCardHash(client.getCardHash());

        claim = claimRepository.save(claim);

        // ─── Verify against POS webhook data ───
        var verification = verificationService.verify(claim);
        String verificationStatus = verification.status;
        int verificationScore = verification.score;

        auditLogService.log("MOBILE_CLAIM_CREATED", "mobile", "Claim",
            claim.getId(), client.getPhone(), "SUCCESS",
            "Cashback claim created: " + claim.getClaimReference()
            + " amount=" + cashbackAmount + " FCFA"
            + " campaign=" + campaignName
            + " card=" + client.getMaskedCard()
            + " verification=" + verificationStatus + "(" + verificationScore + ")");

        // Update loyalty points
        int pointsEarned = totalAmount.intValue() / 1000;
        client.setTierPoints(client.getTierPoints() + pointsEarned);
        updateTier(client);
        clientRepository.save(client);

        // Return result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUBMITTED");
        result.put("claimReference", claim.getClaimReference());
        result.put("claimId", claim.getId());
        result.put("ticketId", ticket.getId());
        result.put("merchant", merchantName);
        result.put("campaign", campaignName);
        result.put("cashbackRate", cashbackRate);
        result.put("ticketAmount", totalAmount);
        result.put("cashbackAmount", cashbackAmount);
        result.put("currency", "FCFA");
        result.put("submittedAt", claim.getSubmittedAt().toString());
        result.put("pointsEarned", pointsEarned);
        result.put("maskedCard", client.getMaskedCard());
        result.put("cardVerified", client.getCardHash() != null && !client.getCardHash().isEmpty());
        result.put("posVerification", verificationStatus);
        result.put("posVerificationScore", verificationScore);
        if (verification.matchedTransaction != null) {
            result.put("posTransactionRef", verification.matchedTransaction.getTransactionRef());
            result.put("posAmount", verification.matchedTransaction.getAmount());
        }

        return ResponseEntity.ok(result);
    }

    private Merchant findMerchantByName(String name) {
        return merchantRepository.findAll().stream()
            .filter(m -> m.getName().equalsIgnoreCase(name)
                || (m.getBrandName() != null && m.getBrandName().equalsIgnoreCase(name)))
            .findFirst()
            .orElse(null);
    }

    private Campaign findActiveCampaign(Long merchantId) {
        List<Campaign> campaigns = campaignRepository.findByMerchantId(merchantId);
        LocalDate today = LocalDate.now();
        return campaigns.stream()
            .filter(c -> c.getStatus() == CampaignStatus.ACTIVE)
            .filter(c -> c.getStartDate() == null || !today.isBefore(c.getStartDate()))
            .filter(c -> c.getEndDate() == null || !today.isAfter(c.getEndDate()))
            .findFirst()
            .orElse(null);
    }

    private void updateTier(MobileClient client) {
        int points = client.getTierPoints();
        if (points >= 10000) client.setTier("Platine");
        else if (points >= 5000) client.setTier("Or");
        else if (points >= 2000) client.setTier("Argent");
        else client.setTier("Bronze");
    }

    private String randomRef() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
