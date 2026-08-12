package com.afriland.ticket2cash.dashboard;

import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.cashback.CashbackPaymentStatus;
import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.fraud.FraudAlert;
import com.afriland.ticket2cash.fraud.FraudAlertRepository;
import com.afriland.ticket2cash.fraud.FraudAlertStatus;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.merchant.MerchantStatus;
import com.afriland.ticket2cash.product.Product;
import com.afriland.ticket2cash.product.ProductRepository;
import com.afriland.ticket2cash.ticket.Ticket;
import com.afriland.ticket2cash.ticket.TicketRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/business-dashboard")
public class BusinessDashboardController {

    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;
    private final TicketRepository ticketRepository;
    private final ClaimRepository claimRepository;
    private final CashbackPaymentRepository paymentRepository;
    private final FraudAlertRepository fraudAlertRepository;

    public BusinessDashboardController(MerchantRepository merchantRepository,
                                       ProductRepository productRepository,
                                       CampaignRepository campaignRepository,
                                       TicketRepository ticketRepository,
                                       ClaimRepository claimRepository,
                                       CashbackPaymentRepository paymentRepository,
                                       FraudAlertRepository fraudAlertRepository) {
        this.merchantRepository = merchantRepository;
        this.productRepository = productRepository;
        this.campaignRepository = campaignRepository;
        this.ticketRepository = ticketRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.fraudAlertRepository = fraudAlertRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<Merchant> merchants = merchantRepository.findAll();
        List<Product> products = productRepository.findAll();
        List<Campaign> campaigns = campaignRepository.findAll();
        List<Ticket> tickets = ticketRepository.findAll();
        List<Claim> claims = claimRepository.findAll();
        List<CashbackPayment> payments = paymentRepository.findAll();
        List<FraudAlert> alerts = fraudAlertRepository.findAll();

        long activeMerchants = merchants.stream()
                .filter(m -> m.getStatus() == MerchantStatus.ACTIVE)
                .count();

        long activeProducts = products.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .count();

        long activeCampaigns = campaigns.stream()
                .filter(c -> c.getStatus() == CampaignStatus.ACTIVE)
                .count();

        long submittedClaims = claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.SUBMITTED)
                .count();

        long approvedClaims = claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
                .count();

        long paidClaims = claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.PAID)
                .count();

        long rejectedClaims = claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.REJECTED)
                .count();

        long openFraudAlerts = alerts.stream()
                .filter(a -> a.getStatus() == FraudAlertStatus.OPEN)
                .count();

        BigDecimal totalCashbackPaid = payments.stream()
                .filter(p -> p.getStatus() == CashbackPaymentStatus.SUCCESS)
                .map(CashbackPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("totalMerchants", merchants.size());
        response.put("activeMerchants", activeMerchants);
        response.put("totalProducts", products.size());
        response.put("activeProducts", activeProducts);
        response.put("totalCampaigns", campaigns.size());
        response.put("activeCampaigns", activeCampaigns);
        response.put("totalTickets", tickets.size());
        response.put("totalClaims", claims.size());
        response.put("submittedClaims", submittedClaims);
        response.put("approvedClaims", approvedClaims);
        response.put("paidClaims", paidClaims);
        response.put("rejectedClaims", rejectedClaims);
        response.put("totalPayments", payments.size());
        response.put("totalCashbackPaid", totalCashbackPaid);
        response.put("totalFraudAlerts", alerts.size());
        response.put("openFraudAlerts", openFraudAlerts);

        response.put("topMerchantsByClaims", buildTopMerchantsByClaims(merchants, claims));
        response.put("recentPayments", buildRecentPayments(payments));
        response.put("recentFraudAlerts", buildRecentFraudAlerts(alerts));
        response.put("recentClaims", buildRecentClaims(claims));

        return response;
    }

    private List<Map<String, Object>> buildTopMerchantsByClaims(List<Merchant> merchants, List<Claim> claims) {
        Map<Long, Long> countByMerchant = new LinkedHashMap<>();

        for (Claim claim : claims) {
            if (claim.getMerchantId() != null) {
                countByMerchant.put(
                        claim.getMerchantId(),
                        countByMerchant.getOrDefault(claim.getMerchantId(), 0L) + 1
                );
            }
        }

        return countByMerchant.entrySet()
                .stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    Long merchantId = entry.getKey();

                    String merchantName = merchants.stream()
                            .filter(m -> Objects.equals(m.getId(), merchantId))
                            .map(Merchant::getName)
                            .findFirst()
                            .orElse("Merchant " + merchantId);

                    map.put("merchantId", merchantId);
                    map.put("merchantName", merchantName);
                    map.put("claimsCount", entry.getValue());

                    return map;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRecentPayments(List<CashbackPayment> payments) {
        return payments.stream()
                .sorted(Comparator.comparing(CashbackPayment::getProcessedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(payment -> {
                    Map<String, Object> map = new LinkedHashMap<>();

                    map.put("id", payment.getId());
                    map.put("reference", payment.getPaymentReference());
                    map.put("claimId", payment.getClaimId());
                    map.put("userId", payment.getUserId());
                    map.put("merchantId", payment.getMerchantId());
                    map.put("amount", payment.getAmount());
                    map.put("currency", payment.getCurrency());
                    map.put("status", payment.getStatus());
                    map.put("processedAt", payment.getProcessedAt());

                    return map;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRecentFraudAlerts(List<FraudAlert> alerts) {
        return alerts.stream()
                .sorted(Comparator.comparing(FraudAlert::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(alert -> {
                    Map<String, Object> map = new LinkedHashMap<>();

                    map.put("id", alert.getId());
                    map.put("reference", alert.getAlertReference());
                    map.put("ticketId", alert.getTicketId());
                    map.put("claimId", alert.getClaimId());
                    map.put("merchantId", alert.getMerchantId());
                    map.put("userId", alert.getUserId());
                    map.put("riskScore", alert.getRiskScore());
                    map.put("reason", alert.getReason());
                    map.put("status", alert.getStatus());
                    map.put("createdAt", alert.getCreatedAt());

                    return map;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRecentClaims(List<Claim> claims) {
        return claims.stream()
                .sorted(Comparator.comparing(Claim::getSubmittedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(claim -> {
                    Map<String, Object> map = new LinkedHashMap<>();

                    map.put("id", claim.getId());
                    map.put("reference", claim.getClaimReference());
                    map.put("userId", claim.getUserId());
                    map.put("merchantId", claim.getMerchantId());
                    map.put("campaignId", claim.getCampaignId());
                    map.put("ticketId", claim.getTicketId());
                    map.put("ticketAmount", claim.getTicketAmount());
                    map.put("cashbackAmount", claim.getCashbackAmount());
                    map.put("status", claim.getStatus());
                    map.put("submittedAt", claim.getSubmittedAt());

                    return map;
                })
                .toList();
    }
}