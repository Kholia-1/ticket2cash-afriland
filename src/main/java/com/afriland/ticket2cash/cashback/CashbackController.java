package com.afriland.ticket2cash.cashback;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.claim.ClaimStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cashback")
public class CashbackController {

    private final CashbackPaymentRepository paymentRepository;
    private final ClaimRepository claimRepository;
    private final AuditLogService auditLogService;

    public CashbackController(CashbackPaymentRepository paymentRepository,
                              ClaimRepository claimRepository,
                              AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository;
        this.claimRepository = claimRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/payments")
    public List<CashbackPayment> getAllPayments() {
        return paymentRepository.findAll();
    }

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