package com.afriland.ticket2cash.mobile;

import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.ticket.Ticket;
import com.afriland.ticket2cash.ticket.TicketRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Partner-specific dashboard API.
 * Returns KPIs, analytics, and reports filtered by the partner's merchantId.
 */
@RestController
@RequestMapping("/api/partner")
public class PartnerDashboardController {

    private final ClaimRepository claimRepository;
    private final TicketRepository ticketRepository;
    private final CampaignRepository campaignRepository;
    private final MerchantRepository merchantRepository;
    private final CashbackPaymentRepository paymentRepository;

    public PartnerDashboardController(ClaimRepository claimRepository,
                                       TicketRepository ticketRepository,
                                       CampaignRepository campaignRepository,
                                       MerchantRepository merchantRepository,
                                       CashbackPaymentRepository paymentRepository) {
        this.claimRepository = claimRepository;
        this.ticketRepository = ticketRepository;
        this.campaignRepository = campaignRepository;
        this.merchantRepository = merchantRepository;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getPartnerDashboard(HttpServletRequest request) {
        Long merchantId = getMerchantId(request);
        if (merchantId == null) {
            return ResponseEntity.status(403).body(Map.of("error", "No merchant linked"));
        }

        List<Claim> allClaims = claimRepository.findByMerchantId(merchantId);
        List<Ticket> allTickets = ticketRepository.findAll().stream()
            .filter(t -> merchantId.equals(t.getMerchantId())).collect(Collectors.toList());

        // KPIs
        int totalClaims = allClaims.size();
        int approvedClaims = (int) allClaims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.APPROVED || c.getStatus() == ClaimStatus.PAID).count();
        int pendingClaims = (int) allClaims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.SUBMITTED).count();
        int rejectedClaims = (int) allClaims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.REJECTED).count();

        BigDecimal totalSales = allClaims.stream()
            .map(c -> c.getTicketAmount() != null ? c.getTicketAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashbackPaid = allClaims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.PAID)
            .map(c -> c.getCashbackAmount() != null ? c.getCashbackAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingCashback = allClaims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.SUBMITTED || c.getStatus() == ClaimStatus.APPROVED)
            .map(c -> c.getCashbackAmount() != null ? c.getCashbackAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Unique customers
        long uniqueCustomers = allClaims.stream()
            .map(Claim::getUserId).filter(Objects::nonNull).distinct().count();

        // Today's stats
        LocalDate today = LocalDate.now();
        long todayClaims = allClaims.stream()
            .filter(c -> c.getSubmittedAt() != null && c.getSubmittedAt().toLocalDate().equals(today)).count();

        BigDecimal todaySales = allClaims.stream()
            .filter(c -> c.getSubmittedAt() != null && c.getSubmittedAt().toLocalDate().equals(today))
            .map(c -> c.getTicketAmount() != null ? c.getTicketAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Active campaigns
        List<Campaign> campaigns = campaignRepository.findByMerchantId(merchantId);
        long activeCampaigns = campaigns.stream()
            .filter(c -> c.getStatus() == com.afriland.ticket2cash.campaign.CampaignStatus.ACTIVE).count();

        // Merchant info
        String merchantName = "";
        Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
        if (merchant != null) merchantName = merchant.getName();

        // Average basket
        int avgBasket = totalClaims > 0 ? totalSales.intValue() / totalClaims : 0;

        // Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantName", merchantName);
        result.put("merchantId", merchantId);

        // KPIs
        result.put("totalClaims", totalClaims);
        result.put("approvedClaims", approvedClaims);
        result.put("pendingClaims", pendingClaims);
        result.put("rejectedClaims", rejectedClaims);
        result.put("totalSales", totalSales);
        result.put("totalCashbackPaid", totalCashbackPaid);
        result.put("pendingCashback", pendingCashback);
        result.put("uniqueCustomers", uniqueCustomers);
        result.put("averageBasket", avgBasket);
        result.put("activeCampaigns", activeCampaigns);
        result.put("totalTickets", allTickets.size());

        // Today
        result.put("todayClaims", todayClaims);
        result.put("todaySales", todaySales);

        // Settlement summary
        result.put("settlementPaid", totalCashbackPaid);
        result.put("settlementPending", pendingCashback);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/recent-claims")
    public ResponseEntity<?> getRecentClaims(HttpServletRequest request) {
        Long merchantId = getMerchantId(request);
        if (merchantId == null) {
            return ResponseEntity.status(403).body(Map.of("error", "No merchant linked"));
        }

        List<Claim> claims = claimRepository.findByMerchantId(merchantId);
        claims.sort(Comparator.comparing(Claim::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<Map<String, Object>> result = claims.stream().limit(20).map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("ref", c.getClaimReference());
            map.put("customer", c.getUserId());
            map.put("amount", c.getTicketAmount());
            map.put("cashback", c.getCashbackAmount());
            map.put("status", c.getStatus() != null ? c.getStatus().name() : "");
            map.put("date", c.getSubmittedAt() != null ? c.getSubmittedAt().toString() : "");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private Long getMerchantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object mid = session.getAttribute("AUTH_MERCHANT_ID");
        if (mid instanceof Long) return (Long) mid;
        if (mid instanceof Number) return ((Number) mid).longValue();
        return null;
    }
}
