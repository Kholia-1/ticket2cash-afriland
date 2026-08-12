package com.afriland.ticket2cash.demo;

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
import com.afriland.ticket2cash.product.CashbackType;
import com.afriland.ticket2cash.product.Product;
import com.afriland.ticket2cash.product.ProductRepository;
import com.afriland.ticket2cash.ticket.Ticket;
import com.afriland.ticket2cash.ticket.TicketRepository;
import com.afriland.ticket2cash.ticket.TicketStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/demo-data")
public class DemoDataController {

    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;
    private final TicketRepository ticketRepository;
    private final ClaimRepository claimRepository;
    private final CashbackPaymentRepository paymentRepository;
    private final FraudAlertRepository fraudAlertRepository;

    public DemoDataController(MerchantRepository merchantRepository,
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

    @PostMapping("/init")
    public ResponseEntity<?> initDemoData(HttpServletRequest request) {

        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        boolean alreadyInitialized = merchantRepository.findAll()
                .stream()
                .anyMatch(m -> "DEMO-T2C-AKWA".equals(m.getNiu()));

        if (alreadyInitialized) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Demo data already initialized");
            response.put("merchants", merchantRepository.findAll().size());
            response.put("products", productRepository.findAll().size());
            response.put("campaigns", campaignRepository.findAll().size());
            response.put("tickets", ticketRepository.findAll().size());
            response.put("claims", claimRepository.findAll().size());
            response.put("payments", paymentRepository.findAll().size());
            response.put("fraudAlerts", fraudAlertRepository.findAll().size());
            return ResponseEntity.ok(response);
        }

        Merchant akwa = createMerchant(
                "Supermarche Demo Akwa",
                "Demo Akwa Market",
                "DEMO-T2C-AKWA",
                "Douala"
        );

        Merchant biyem = createMerchant(
                "Supermarche Demo Biyem-Assi",
                "Demo Biyem Market",
                "DEMO-T2C-BIYEM",
                "Yaounde"
        );

        Merchant bonamoussadi = createMerchant(
                "Supermarche Demo Bonamoussadi",
                "Demo Bonamoussadi Market",
                "DEMO-T2C-BONA",
                "Douala"
        );

        Product riz = createProduct(akwa, "RIZ-25KG", "Riz premium 25KG", "Alimentation", "RIZ PREMIUM 25KG", new BigDecimal("18500"), CashbackType.PERCENTAGE, new BigDecimal("5"));
        Product huile = createProduct(akwa, "HUILE-5L", "Huile vegetale 5L", "Alimentation", "HUILE VEGETALE 5L", new BigDecimal("7500"), CashbackType.PERCENTAGE, new BigDecimal("3"));
        Product lait = createProduct(biyem, "LAIT-1KG", "Lait en poudre 1KG", "Epicerie", "LAIT POUDRE 1KG", new BigDecimal("4200"), CashbackType.FIXED_AMOUNT, new BigDecimal("250"));
        Product savon = createProduct(biyem, "SAVON-LOT", "Lot savon menager", "Hygiene", "SAVON MENAGER LOT", new BigDecimal("3000"), CashbackType.FIXED_AMOUNT, new BigDecimal("150"));
        Product sucre = createProduct(bonamoussadi, "SUCRE-5KG", "Sucre blanc 5KG", "Alimentation", "SUCRE BLANC 5KG", new BigDecimal("4900"), CashbackType.PERCENTAGE, new BigDecimal("4"));

        Campaign campagneAkwa = createCampaign(
                akwa,
                "Campagne rentree Akwa",
                "Cashback sur produits alimentaires",
                CashbackType.PERCENTAGE,
                new BigDecimal("5"),
                new BigDecimal("1000000")
        );

        Campaign campagneBiyem = createCampaign(
                biyem,
                "Campagne famille Biyem",
                "Cashback hygiene et epicerie",
                CashbackType.FIXED_AMOUNT,
                new BigDecimal("300"),
                new BigDecimal("750000")
        );

        Campaign campagneBona = createCampaign(
                bonamoussadi,
                "Campagne weekend Bonamoussadi",
                "Cashback sur panier supermarche",
                CashbackType.PERCENTAGE,
                new BigDecimal("4"),
                new BigDecimal("500000")
        );

        Ticket t1 = createTicket(akwa, "TCK-DEMO-001", new BigDecimal("44500"), 12);
        Ticket t2 = createTicket(akwa, "TCK-DEMO-002", new BigDecimal("26500"), 10);
        Ticket t3 = createTicket(akwa, "TCK-DEMO-003", new BigDecimal("18500"), 9);
        Ticket t4 = createTicket(biyem, "TCK-DEMO-004", new BigDecimal("31200"), 8);
        Ticket t5 = createTicket(biyem, "TCK-DEMO-005", new BigDecimal("17800"), 7);
        Ticket t6 = createTicket(bonamoussadi, "TCK-DEMO-006", new BigDecimal("52900"), 6);
        Ticket t7 = createTicket(bonamoussadi, "TCK-DEMO-007", new BigDecimal("9200"), 5);
        Ticket t8 = createTicket(akwa, "TCK-DEMO-008", new BigDecimal("44500"), 4);

        Claim c1 = createClaim("CL-DEMO-001", "CLIENT001", akwa, campagneAkwa, t1, new BigDecimal("44500"), new BigDecimal("2225"), ClaimStatus.PAID, 12);
        Claim c2 = createClaim("CL-DEMO-002", "CLIENT002", akwa, campagneAkwa, t2, new BigDecimal("26500"), new BigDecimal("1325"), ClaimStatus.APPROVED, 10);
        Claim c3 = createClaim("CL-DEMO-003", "CLIENT003", akwa, campagneAkwa, t3, new BigDecimal("18500"), new BigDecimal("925"), ClaimStatus.SUBMITTED, 9);
        Claim c4 = createClaim("CL-DEMO-004", "CLIENT004", biyem, campagneBiyem, t4, new BigDecimal("31200"), new BigDecimal("300"), ClaimStatus.PAID, 8);
        Claim c5 = createClaim("CL-DEMO-005", "CLIENT005", biyem, campagneBiyem, t5, new BigDecimal("17800"), new BigDecimal("300"), ClaimStatus.REJECTED, 7);
        Claim c6 = createClaim("CL-DEMO-006", "CLIENT006", bonamoussadi, campagneBona, t6, new BigDecimal("52900"), new BigDecimal("2116"), ClaimStatus.PAID, 6);
        Claim c7 = createClaim("CL-DEMO-007", "CLIENT007", bonamoussadi, campagneBona, t7, new BigDecimal("9200"), new BigDecimal("368"), ClaimStatus.APPROVED, 5);
        Claim c8 = createClaim("CL-DEMO-008", "CLIENT001", akwa, campagneAkwa, t8, new BigDecimal("44500"), new BigDecimal("2225"), ClaimStatus.SUBMITTED, 4);

        createPayment("PAY-DEMO-001", c1, CashbackPaymentStatus.SUCCESS, 11);
        createPayment("PAY-DEMO-002", c4, CashbackPaymentStatus.SUCCESS, 7);
        createPayment("PAY-DEMO-003", c6, CashbackPaymentStatus.SUCCESS, 5);

        createFraudAlert("FRD-DEMO-001", t8, c8, akwa, "CLIENT001", 95, "Suspicion doublon ticket montant identique", FraudAlertStatus.OPEN, 4);
        createFraudAlert("FRD-DEMO-002", t5, c5, biyem, "CLIENT005", 72, "Ticket peu lisible / montant a verifier", FraudAlertStatus.UNDER_REVIEW, 6);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Demo data initialized successfully");
        response.put("merchantsCreated", 3);
        response.put("productsCreated", 5);
        response.put("campaignsCreated", 3);
        response.put("ticketsCreated", 8);
        response.put("claimsCreated", 8);
        response.put("paymentsCreated", 3);
        response.put("fraudAlertsCreated", 2);

        return ResponseEntity.ok(response);
    }

    private Merchant createMerchant(String name, String brandName, String niu, String city) {
        Merchant merchant = new Merchant();
        merchant.setName(name);
        merchant.setBrandName(brandName);
        merchant.setRccm("RCCM-" + niu);
        merchant.setNiu(niu);
        merchant.setPhone("690000000");
        merchant.setEmail(niu.toLowerCase() + "@demo.local");
        merchant.setCity(city);
        merchant.setAddress("Adresse demo " + city);
        merchant.setStatus(MerchantStatus.ACTIVE);
        return merchantRepository.save(merchant);
    }

    private Product createProduct(Merchant merchant,
                                  String sku,
                                  String name,
                                  String category,
                                  String ticketDesignation,
                                  BigDecimal price,
                                  CashbackType cashbackType,
                                  BigDecimal cashbackValue) {
        Product product = new Product();
        product.setMerchant(merchant);
        product.setSku(sku);
        product.setName(name);
        product.setTicketDesignation(ticketDesignation);
        product.setSynonyms(name + ";" + ticketDesignation);
        product.setCategory(category);
        product.setBrand("DEMO");
        product.setPrice(price);
        product.setGroupKey(category);
        product.setCashbackType(cashbackType);
        product.setCashbackValue(cashbackValue);
        product.setActive(true);
        return productRepository.save(product);
    }

    private Campaign createCampaign(Merchant merchant,
                                    String name,
                                    String description,
                                    CashbackType cashbackType,
                                    BigDecimal cashbackValue,
                                    BigDecimal totalBudget) {
        Campaign campaign = new Campaign();
        campaign.setMerchant(merchant);
        campaign.setName(name);
        campaign.setDescription(description);
        campaign.setStartDate(LocalDate.now().minusDays(10));
        campaign.setEndDate(LocalDate.now().plusDays(30));
        campaign.setCashbackType(cashbackType);
        campaign.setCashbackValue(cashbackValue);
        campaign.setDailyLimitPerClient(new BigDecimal("2"));
        campaign.setMonthlyLimitPerClient(new BigDecimal("10"));
        campaign.setTotalBudget(totalBudget);
        campaign.setStatus(CampaignStatus.ACTIVE);
        return campaignRepository.save(campaign);
    }

    private Ticket createTicket(Merchant merchant,
                                String ticketNumber,
                                BigDecimal totalAmount,
                                int daysAgo) {
        Ticket ticket = new Ticket();
        ticket.setMerchantId(merchant.getId());
        ticket.setTicketNumber(ticketNumber);
        ticket.setStoreName(merchant.getName());
        ticket.setTicketDateTime(LocalDateTime.now().minusDays(daysAgo));
        ticket.setTotalAmount(totalAmount);
        ticket.setCurrency("FCFA");
        ticket.setTicketHash("HASH-" + ticketNumber);
        ticket.setFraudScore(20);
        ticket.setStatus(TicketStatus.OCR_PROCESSED);
        ticket.setOcrRawText("OCR DEMO - " + ticketNumber + " - TOTAL " + totalAmount + " FCFA");
        return ticketRepository.save(ticket);
    }

    private Claim createClaim(String reference,
                              String userId,
                              Merchant merchant,
                              Campaign campaign,
                              Ticket ticket,
                              BigDecimal ticketAmount,
                              BigDecimal cashbackAmount,
                              ClaimStatus status,
                              int daysAgo) {
        Claim claim = new Claim();
        claim.setClaimReference(reference);
        claim.setUserId(userId);
        claim.setMerchantId(merchant.getId());
        claim.setCampaignId(campaign.getId());
        claim.setTicketId(ticket.getId());
        claim.setTicketAmount(ticketAmount);
        claim.setCashbackAmount(cashbackAmount);
        claim.setStatus(status);
        claim.setSubmittedAt(LocalDateTime.now().minusDays(daysAgo));
        return claimRepository.save(claim);
    }

    private CashbackPayment createPayment(String reference,
                                          Claim claim,
                                          CashbackPaymentStatus status,
                                          int daysAgo) {
        CashbackPayment payment = new CashbackPayment();
        payment.setPaymentReference(reference);
        payment.setClaimId(claim.getId());
        payment.setMerchantId(claim.getMerchantId());
        payment.setCampaignId(claim.getCampaignId());
        payment.setUserId(claim.getUserId());
        payment.setAmount(claim.getCashbackAmount());
        payment.setCurrency("FCFA");
        payment.setStatus(status);
        payment.setProcessedAt(LocalDateTime.now().minusDays(daysAgo));
        return paymentRepository.save(payment);
    }

    private FraudAlert createFraudAlert(String reference,
                                        Ticket ticket,
                                        Claim claim,
                                        Merchant merchant,
                                        String userId,
                                        Integer riskScore,
                                        String reason,
                                        FraudAlertStatus status,
                                        int daysAgo) {
        FraudAlert alert = new FraudAlert();
        alert.setAlertReference(reference);
        alert.setTicketId(ticket.getId());
        alert.setClaimId(claim.getId());
        alert.setMerchantId(merchant.getId());
        alert.setUserId(userId);
        alert.setRiskScore(riskScore);
        alert.setReason(reason);
        alert.setStatus(status);
        alert.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        return fraudAlertRepository.save(alert);
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        return session != null
                && "ADMIN".equals(String.valueOf(session.getAttribute("AUTH_ROLE")));
    }
}