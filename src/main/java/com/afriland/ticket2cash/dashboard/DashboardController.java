package com.afriland.ticket2cash.dashboard;

import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.fraud.FraudAlertRepository;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.product.ProductRepository;
import com.afriland.ticket2cash.ticket.TicketRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DashboardController {

    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;
    private final TicketRepository ticketRepository;
    private final ClaimRepository claimRepository;
    private final CashbackPaymentRepository cashbackPaymentRepository;
    private final FraudAlertRepository fraudAlertRepository;

    public DashboardController(MerchantRepository merchantRepository,
                               ProductRepository productRepository,
                               CampaignRepository campaignRepository,
                               TicketRepository ticketRepository,
                               ClaimRepository claimRepository,
                               CashbackPaymentRepository cashbackPaymentRepository,
                               FraudAlertRepository fraudAlertRepository) {
        this.merchantRepository = merchantRepository;
        this.productRepository = productRepository;
        this.campaignRepository = campaignRepository;
        this.ticketRepository = ticketRepository;
        this.claimRepository = claimRepository;
        this.cashbackPaymentRepository = cashbackPaymentRepository;
        this.fraudAlertRepository = fraudAlertRepository;
    }

    @GetMapping("/api/dashboard/summary")
    public Map<String, Object> getDashboardSummary() {

        BigDecimal cashbackTotalPaye = cashbackPaymentRepository.findAll()
                .stream()
                .map(CashbackPayment::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("commercantsPartenaires", merchantRepository.count());
        summary.put("produitsCatalogues", productRepository.count());
        summary.put("campagnesCashback", campaignRepository.count());
        summary.put("ticketsOcr", ticketRepository.count());

        summary.put("claimsSoumis", claimRepository.count());
        summary.put("claimsEnAttente", claimRepository.findByStatus(ClaimStatus.SUBMITTED).size());
        summary.put("claimsApprouves", claimRepository.findByStatus(ClaimStatus.APPROVED).size());
        summary.put("claimsPayes", claimRepository.findByStatus(ClaimStatus.PAID).size());
        summary.put("claimsRejetes", claimRepository.findByStatus(ClaimStatus.REJECTED).size());

        summary.put("paiementsCashback", cashbackPaymentRepository.count());
        summary.put("cashbackTotalPaye", cashbackTotalPaye);
        summary.put("alertesFraude", fraudAlertRepository.count());

        summary.put("devise", "FCFA");
        summary.put("application", "Ticket2Cash");
        summary.put("module", "Cashback Supermarches");
        summary.put("status", "RUNNING");

        return summary;
    }
}