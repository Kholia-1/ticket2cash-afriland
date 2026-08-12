package com.afriland.ticket2cash.mobile;

import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.pos.PosTransaction;
import com.afriland.ticket2cash.pos.PosTransactionRepository;
import com.afriland.ticket2cash.product.CashbackType;
import com.afriland.ticket2cash.ticket.Ticket;
import com.afriland.ticket2cash.ticket.TicketRepository;
import com.afriland.ticket2cash.ticket.TicketStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Phase 2 (validate) + Phase 3 (reconcile) of ticket verification.
 * Phase 1 (OCR) is handled separately by OcrController.
 *
 * POST /api/mobile/verify-ticket
 * Body: { merchantName?, merchantId?, ticketNumber?, totalAmount, ticketDate,
 *         cardNumber?, cardHash?, maskedCard?, campaignId?, userId?, submit? }
 *
 * By default this is a DRY RUN (no persistence). If "submit": true and the
 * verdict is not REJECTED, it records a Ticket (enables duplicate detection)
 * and a Claim carrying the verdict, fraud score and review notes.
 */
@RestController
@RequestMapping("/api/mobile")
@CrossOrigin(origins = "*")
public class TicketVerificationController {

    private static final int MAX_TICKET_AGE_DAYS = 30;

    private final TicketRepository ticketRepository;
    private final MerchantRepository merchantRepository;
    private final CampaignRepository campaignRepository;
    private final PosTransactionRepository posRepository;
    private final ClaimRepository claimRepository;

    public TicketVerificationController(TicketRepository ticketRepository,
                                        MerchantRepository merchantRepository,
                                        CampaignRepository campaignRepository,
                                        PosTransactionRepository posRepository,
                                        ClaimRepository claimRepository) {
        this.ticketRepository = ticketRepository;
        this.merchantRepository = merchantRepository;
        this.campaignRepository = campaignRepository;
        this.posRepository = posRepository;
        this.claimRepository = claimRepository;
    }

    @PostMapping("/verify-ticket")
    public ResponseEntity<?> verify(@RequestBody Map<String, Object> body) {

        String merchantName = str(body.get("merchantName"));
        Long merchantId = longOrNull(body.get("merchantId"));
        String ticketNumber = str(body.get("ticketNumber"));
        BigDecimal amount = decOrNull(body.get("totalAmount"));
        LocalDateTime ticketDate = parseDate(body.get("ticketDate"));
        String cardNumber = str(body.get("cardNumber"));
        String cardHash = str(body.get("cardHash"));
        if ((cardHash == null || cardHash.isBlank()) && cardNumber != null) {
            cardHash = sha256(cardNumber.replaceAll("\\s", ""));
        }
        String maskedCard = str(body.get("maskedCard"));
        Long campaignId = longOrNull(body.get("campaignId"));
        String userId = str(body.get("userId"));
        boolean submit = Boolean.TRUE.equals(body.get("submit"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("submit")));

        // Quality signals coming from the OCR server (/api/mobile/ocr).
        // When the OCR is not confident (faded / blurry ticket), the values may
        // be wrong even though every rule below passes: never auto-approve then.
        boolean ocrNeedsReview = Boolean.TRUE.equals(body.get("ocrNeedsReview"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("ocrNeedsReview")));
        Double ocrConfidence = null;
        if (body.get("ocrConfidence") != null) {
            try { ocrConfidence = Double.valueOf(String.valueOf(body.get("ocrConfidence"))); }
            catch (Exception ignored) { }
        }
        if (ocrConfidence != null && ocrConfidence < 0.85) ocrNeedsReview = true;

        Map<String, Boolean> checks = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        int fraudScore = 0;

        // ---- Phase 2: validate ----
        boolean ocrComplete = amount != null && ticketDate != null
                && (merchantId != null || (merchantName != null && !merchantName.isBlank()));
        checks.put("ocrComplete", ocrComplete);
        if (!ocrComplete) reasons.add("Champs OCR manquants (montant, date ou commercant).");

        Merchant merchant = null;
        if (merchantId != null) merchant = merchantRepository.findById(merchantId).orElse(null);
        if (merchant == null && merchantName != null) merchant = findMerchantByName(merchantName);
        boolean merchantKnown = merchant != null;
        checks.put("merchantKnown", merchantKnown);
        if (!merchantKnown) { reasons.add("Commercant inconnu."); fraudScore += 40; }
        else merchantId = merchant.getId();

        boolean amountValid = amount != null && amount.signum() > 0;
        checks.put("amountValid", amountValid);
        if (!amountValid) { reasons.add("Montant invalide."); fraudScore += 30; }

        boolean dateValid = false;
        if (ticketDate != null) {
            LocalDateTime now = LocalDateTime.now();
            dateValid = !ticketDate.isAfter(now) && ticketDate.isAfter(now.minusDays(MAX_TICKET_AGE_DAYS));
        }
        checks.put("dateValid", dateValid);
        if (!dateValid) { reasons.add("Date du ticket invalide (future ou trop ancienne)."); fraudScore += 20; }

        String ticketHash = sha256((merchantId == null ? "" : merchantId)
                + "|" + safe(ticketNumber)
                + "|" + (amount == null ? "" : amount.stripTrailingZeros().toPlainString())
                + "|" + (ticketDate == null ? "" : ticketDate.toLocalDate()));
        boolean notDuplicate = !ticketRepository.existsByTicketHash(ticketHash);
        checks.put("notDuplicate", notDuplicate);
        if (!notDuplicate) { reasons.add("Ticket deja utilise (doublon)."); fraudScore += 60; }

        // campaign resolution
        Campaign campaign = null;
        if (campaignId != null) campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null && merchantId != null) {
            for (Campaign c : campaignRepository.findByMerchantId(merchantId)) {
                if (c.getStatus() == CampaignStatus.ACTIVE && withinDates(c, ticketDate)) { campaign = c; break; }
            }
        }
        boolean withinCampaign = campaign != null && withinDates(campaign, ticketDate);
        checks.put("withinCampaign", withinCampaign);
        if (!withinCampaign) { reasons.add("Aucune campagne active correspondante."); fraudScore += 10; }

        BigDecimal cashbackAmount = BigDecimal.ZERO;
        if (withinCampaign && amount != null) cashbackAmount = computeCashback(campaign, amount);

        // ---- Campaign limits (per-client daily/monthly + total budget) ----
        boolean dailyLimitOk = true, monthlyLimitOk = true, budgetOk = true;
        if (campaign != null) {
            List<Claim> campaignClaims = claimRepository.findByCampaignId(campaign.getId());

            if (campaign.getTotalBudget() != null) {
                BigDecimal used = sumCommitted(campaignClaims, null, null, null);
                BigDecimal remaining = campaign.getTotalBudget().subtract(used);
                budgetOk = remaining.signum() > 0;
                cashbackAmount = cashbackAmount.min(remaining.max(BigDecimal.ZERO));
                if (!budgetOk) reasons.add("Budget de la campagne epuise.");
            }
            if (cardHash != null && campaign.getDailyLimitPerClient() != null) {
                BigDecimal used = sumCommitted(campaignClaims, cardHash, LocalDate.now(), null);
                BigDecimal remaining = campaign.getDailyLimitPerClient().subtract(used);
                dailyLimitOk = remaining.signum() > 0;
                cashbackAmount = cashbackAmount.min(remaining.max(BigDecimal.ZERO));
                if (!dailyLimitOk) reasons.add("Limite journaliere par client atteinte.");
            }
            if (cardHash != null && campaign.getMonthlyLimitPerClient() != null) {
                BigDecimal used = sumCommitted(campaignClaims, cardHash, null, LocalDate.now());
                BigDecimal remaining = campaign.getMonthlyLimitPerClient().subtract(used);
                monthlyLimitOk = remaining.signum() > 0;
                cashbackAmount = cashbackAmount.min(remaining.max(BigDecimal.ZERO));
                if (!monthlyLimitOk) reasons.add("Limite mensuelle par client atteinte.");
            }
        }
        checks.put("budgetOk", budgetOk);
        checks.put("dailyLimitOk", dailyLimitOk);
        checks.put("monthlyLimitOk", monthlyLimitOk);
        boolean limitsOk = budgetOk && dailyLimitOk && monthlyLimitOk;

        // ---- Phase 3: reconcile against real POS transaction ----
        boolean transactionMatched = false;
        String matchedRef = null;
        if (merchantId != null && amount != null) {
            List<PosTransaction> candidates = (cardHash != null && !cardHash.isBlank())
                    ? posRepository.findByCardHashAndMatchedFalse(cardHash)
                    : posRepository.findByMerchantId(merchantId);
            for (PosTransaction tx : candidates) {
                if (tx.isMatched()) continue;
                boolean sameMerchant = merchantId.equals(tx.getMerchantId());
                boolean sameAmount = tx.getAmount() != null && tx.getAmount().compareTo(amount) == 0;
                boolean sameDay = tx.getTransactionDate() != null && ticketDate != null
                        && tx.getTransactionDate().toLocalDate().equals(ticketDate.toLocalDate());
                boolean byCard = cardHash != null && !cardHash.isBlank();
                if ((byCard || sameMerchant) && sameAmount && sameDay) {
                    transactionMatched = true;
                    matchedRef = tx.getTransactionRef();
                    break;
                }
            }
        }
        checks.put("transactionMatched", transactionMatched);
        if (!transactionMatched) { reasons.add("Aucune transaction POS correspondante."); fraudScore += 15; }

        checks.put("ocrReliable", !ocrNeedsReview);
        if (ocrNeedsReview) {
            reasons.add("Lecture OCR peu fiable (ticket delave ou photo floue) : verification humaine requise.");
            fraudScore += 10;
        }

        fraudScore = Math.min(100, fraudScore);

        // ---- Verdict ----
        boolean hardFail = !ocrComplete || !merchantKnown || !amountValid || !dateValid || !notDuplicate;
        String verdict;
        if (hardFail) verdict = "REJECTED";
        else if (transactionMatched && withinCampaign && limitsOk && !ocrNeedsReview) verdict = "APPROVED";
        else verdict = "PENDING_REVIEW";

        // ---- Optional persistence ----
        Long claimId = null;
        if (submit && !"REJECTED".equals(verdict)) {
            Ticket ticket = new Ticket();
            ticket.setMerchantId(merchantId);
            ticket.setTicketNumber(ticketNumber);
            ticket.setStoreName(merchant != null ? merchant.getName() : merchantName);
            ticket.setTicketDateTime(ticketDate);
            ticket.setTotalAmount(amount);
            ticket.setCurrency("FCFA");
            ticket.setTicketHash(ticketHash);
            ticket.setFraudScore(fraudScore);
            ticket.setStatus(TicketStatus.OCR_PROCESSED);
            ticket = ticketRepository.save(ticket);

            Claim claim = new Claim();
            claim.setClaimReference("CLM-" + System.currentTimeMillis());
            claim.setUserId(userId);
            claim.setMerchantId(merchantId);
            claim.setCampaignId(campaign != null ? campaign.getId() : null);
            claim.setTicketId(ticket.getId());
            claim.setTicketAmount(amount);
            claim.setCashbackAmount(cashbackAmount);
            claim.setCardHash(cardHash);
            claim.setMaskedCard(maskedCard);
            claim.setFraudScore(fraudScore);
            claim.setReviewNotes(String.join(" ", reasons));
            claim.setStatus("APPROVED".equals(verdict) ? ClaimStatus.APPROVED : ClaimStatus.SUBMITTED);
            claim = claimRepository.save(claim);
            claimId = claim.getId();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verdict", verdict);
        out.put("fraudScore", fraudScore);
        out.put("cashbackAmount", cashbackAmount);
        out.put("ticketHash", ticketHash);
        out.put("merchantId", merchantId);
        out.put("merchantName", merchant != null ? merchant.getName() : merchantName);
        out.put("campaignId", campaign != null ? campaign.getId() : null);
        out.put("matchedTransactionRef", matchedRef);
        out.put("ocrNeedsReview", ocrNeedsReview);
        out.put("checks", checks);
        out.put("reasons", reasons);
        out.put("claimId", claimId);
        return ResponseEntity.ok(out);
    }

    // ---------- helpers ----------

    /** Sum committed (APPROVED/PAID) cashback, optionally filtered by card and day/month. */
    private BigDecimal sumCommitted(List<Claim> claims, String cardHash, LocalDate day, LocalDate month) {
        BigDecimal total = BigDecimal.ZERO;
        for (Claim c : claims) {
            if (c.getStatus() != ClaimStatus.APPROVED && c.getStatus() != ClaimStatus.PAID) continue;
            if (cardHash != null && !cardHash.equals(c.getCardHash())) continue;
            if (c.getSubmittedAt() != null) {
                LocalDate d = c.getSubmittedAt().toLocalDate();
                if (day != null && !d.equals(day)) continue;
                if (month != null && (d.getMonthValue() != month.getMonthValue() || d.getYear() != month.getYear())) continue;
            } else if (day != null || month != null) {
                continue;
            }
            if (c.getCashbackAmount() != null) total = total.add(c.getCashbackAmount());
        }
        return total;
    }

    private Merchant findMerchantByName(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase();
        for (Merchant m : merchantRepository.findAll()) {
            String mn = m.getName() == null ? "" : m.getName().toLowerCase();
            String bn = m.getBrandName() == null ? "" : m.getBrandName().toLowerCase();
            if (mn.equals(n) || bn.equals(n) || (!mn.isBlank() && n.contains(mn)) || (!bn.isBlank() && n.contains(bn))) {
                return m;
            }
        }
        return null;
    }

    private boolean withinDates(Campaign c, LocalDateTime ticketDate) {
        if (c == null || ticketDate == null) return false;
        LocalDate d = ticketDate.toLocalDate();
        if (c.getStartDate() != null && d.isBefore(c.getStartDate())) return false;
        if (c.getEndDate() != null && d.isAfter(c.getEndDate())) return false;
        return true;
    }

    private BigDecimal computeCashback(Campaign c, BigDecimal amount) {
        if (c.getCashbackType() == null || c.getCashbackValue() == null) return BigDecimal.ZERO;
        if (c.getCashbackType() == CashbackType.PERCENTAGE) {
            return amount.multiply(c.getCashbackValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        }
        if (c.getCashbackType() == CashbackType.FIXED_AMOUNT) {
            return c.getCashbackValue();
        }
        return BigDecimal.ZERO;
    }

    private LocalDateTime parseDate(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try { return LocalDateTime.parse(s); } catch (Exception ignored) { }
        try { return LocalDate.parse(s).atStartOfDay(); } catch (Exception ignored) { }
        try { return LocalDateTime.parse(s.replace(" ", "T")); } catch (Exception ignored) { }
        return null;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static Long longOrNull(Object o) {
        if (o == null) return null;
        try { return Long.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }

    private static BigDecimal decOrNull(Object o) {
        if (o == null) return null;
        try { return new BigDecimal(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static String sha256(String in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(in.hashCode());
        }
    }
}
