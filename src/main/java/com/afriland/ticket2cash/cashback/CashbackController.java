package com.afriland.ticket2cash.cashback;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cashback")
public class CashbackController {

    private final CashbackPaymentRepository paymentRepository;
    private final ClaimRepository claimRepository;
    private final AuditLogService auditLogService;
    private final CashbackPaymentProcessingService paymentProcessingService;
    private final CampaignRepository campaignRepository;
    private final MerchantRepository merchantRepository;
    private final CashbackCreditService creditService;

    public CashbackController(CashbackPaymentRepository paymentRepository,
                              ClaimRepository claimRepository,
                              AuditLogService auditLogService,
                              CashbackPaymentProcessingService paymentProcessingService,
                              CampaignRepository campaignRepository,
                              MerchantRepository merchantRepository,
                              CashbackCreditService creditService) {
        this.paymentRepository = paymentRepository;
        this.claimRepository = claimRepository;
        this.auditLogService = auditLogService;
        this.paymentProcessingService = paymentProcessingService;
        this.campaignRepository = campaignRepository;
        this.merchantRepository = merchantRepository;
        this.creditService = creditService;
    }

    @GetMapping("/payments")
    public Page<PaymentDto> getAllPayments(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return paymentRepository.findAllByOrderByIdDesc(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")))
                .map(payment -> PaymentDto.from(payment, campaignRepository, merchantRepository));
    }

    public static class PaymentDto {
        private Long id;
        private String paymentReference;
        private String transactionRef;
        private String maskedCard;
        private String merchantName;
        private BigDecimal amount;
        private String currency;
        private CashbackPaymentStatus status;
        private Long campaignId;
        private String campaignName;
        private LocalDateTime processedAt;
        private CashbackCreditStatus creditStatus; private String creditReference; private LocalDateTime creditedAt; private String creditFailureReason;

        static PaymentDto from(CashbackPayment payment, CampaignRepository campaigns,
                               MerchantRepository merchants) {
            PaymentDto dto = new PaymentDto();
            dto.id = payment.getId(); dto.paymentReference = payment.getPaymentReference();
            dto.transactionRef = payment.getTransactionRef(); dto.maskedCard = payment.getMaskedCard();
            dto.amount = payment.getAmount(); dto.currency = payment.getCurrency();
            dto.status = payment.getStatus(); dto.campaignId = payment.getCampaignId();
            dto.processedAt = payment.getProcessedAt();
            dto.creditStatus=payment.getCreditStatus(); dto.creditReference=payment.getCreditReference(); dto.creditedAt=payment.getCreditedAt(); dto.creditFailureReason=payment.getCreditFailureReason();
            if (payment.getMerchantId() != null) dto.merchantName = merchants.findById(payment.getMerchantId()).map(m -> m.getName()).orElse(null);
            if (payment.getCampaignId() != null) dto.campaignName = campaigns.findById(payment.getCampaignId()).map(c -> c.getName()).orElse(null);
            return dto;
        }
        public Long getId(){return id;} public String getPaymentReference(){return paymentReference;}
        public String getTransactionRef(){return transactionRef;} public String getMaskedCard(){return maskedCard;}
        public String getMerchantName(){return merchantName;} public BigDecimal getAmount(){return amount;}
        public String getCurrency(){return currency;} public CashbackPaymentStatus getStatus(){return status;}
        public Long getCampaignId(){return campaignId;} public String getCampaignName(){return campaignName;}
        public LocalDateTime getProcessedAt(){return processedAt;}
        public CashbackCreditStatus getCreditStatus(){return creditStatus;} public String getCreditReference(){return creditReference;} public LocalDateTime getCreditedAt(){return creditedAt;} public String getCreditFailureReason(){return creditFailureReason;}
    }

    @PostMapping("/payments/process-pending")
    public CashbackPaymentProcessingService.ProcessingSummary processPendingPayments() {
        return paymentProcessingService.processPending();
    }

    @PostMapping("/payments/credit-pending")
    public CashbackCreditService.CreditSummary creditPending(HttpServletRequest request) { return creditService.creditPending(request); }

    @PostMapping("/payments/{id}/credit")
    public CashbackCreditService.CreditSummary creditOne(@PathVariable Long id, HttpServletRequest request) { return creditService.creditOne(id, request); }

    @GetMapping("/payments/user/{userId}")
    public List<CashbackPayment> getPaymentsByUser(@PathVariable String userId) {
        return paymentRepository.findByUserId(userId);
    }

    @GetMapping("/payments/merchant/{merchantId}")
    public List<CashbackPayment> getPaymentsByMerchant(@PathVariable Long merchantId) {
        return paymentRepository.findByMerchantId(merchantId);
    }

    @PostMapping("/batch/run")
    public Map<String, Object> runCashbackBatch() {

        List<Claim> approvedClaims = claimRepository.findByStatus(ClaimStatus.APPROVED);

        int processed = 0;

        for (Claim claim : approvedClaims) {
            CashbackPayment payment = new CashbackPayment();

            payment.setPaymentReference("PAY-" + System.currentTimeMillis() + "-" + claim.getId());
            payment.setClaimId(claim.getId());
            payment.setMerchantId(claim.getMerchantId());
            payment.setCampaignId(claim.getCampaignId());
            payment.setUserId(claim.getUserId());
            payment.setAmount(claim.getCashbackAmount());
            payment.setCurrency("FCFA");
            payment.setStatus(CashbackPaymentStatus.SUCCESS);

            CashbackPayment savedPayment = paymentRepository.save(payment);

            claim.setStatus(ClaimStatus.PAID);
            claimRepository.save(claim);

            auditLogService.log(
                    "CASHBACK_PAYMENT_CREATED",
                    "CASHBACK",
                    "CashbackPayment",
                    savedPayment.getId(),
                    "SYSTEM_BATCH",
                    "SUCCESS",
                    "Cashback payment created for claim ID=" + claim.getId()
            );

            processed++;
        }

        auditLogService.log(
                "RUN_CASHBACK_BATCH",
                "CASHBACK",
                "Batch",
                null,
                "SYSTEM_BATCH",
                "SUCCESS",
                "Batch executed. Approved claims=" + approvedClaims.size() + ", payments created=" + processed
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Batch cashback J+1 execute avec succes");
        response.put("claimsApprouvesTrouves", approvedClaims.size());
        response.put("paiementsCrees", processed);
        response.put("status", "SUCCESS");

        return response;
    }
}
