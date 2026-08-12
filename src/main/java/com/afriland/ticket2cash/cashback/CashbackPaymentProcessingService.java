package com.afriland.ticket2cash.cashback;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CashbackPaymentProcessingService {

    private final CashbackPaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final CampaignRepository campaignRepository;
    private final MerchantRepository merchantRepository;
    private final AtomicInteger sequence = new AtomicInteger();

    public CashbackPaymentProcessingService(CashbackPaymentRepository paymentRepository,
                                            AuditLogService auditLogService,
                                            CampaignRepository campaignRepository,
                                            MerchantRepository merchantRepository) {
        this.paymentRepository = paymentRepository;
        this.auditLogService = auditLogService;
        this.campaignRepository = campaignRepository;
        this.merchantRepository = merchantRepository;
    }

    @Transactional
    public ProcessingSummary processPending() {
        List<CashbackPayment> pending = paymentRepository.findByStatusOrderByIdAsc(CashbackPaymentStatus.PENDING);
        ProcessingSummary summary = new ProcessingSummary();
        String batchReference = "BATCH-PAY-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        summary.totalPending = pending.size();
        for (CashbackPayment payment : pending) {
            try {
                if (payment.getStatus() != CashbackPaymentStatus.PENDING) {
                    summary.skipped++;
                    continue;
                }
                String reference = payment.getPaymentReference();
                if (reference == null || reference.isBlank()) {
                    reference = "PAY-AFB-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                            + "-" + String.format("%04d", sequence.incrementAndGet());
                    payment.setPaymentReference(reference);
                }
                payment.setStatus(CashbackPaymentStatus.SUCCESS);
                payment.setProcessedAt(java.time.LocalDateTime.now());
                paymentRepository.save(payment);
                summary.processed++;
                summary.totalAmount = summary.totalAmount.add(payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount());
                summary.paymentReferences.add(reference);
                auditLogService.log("CASHBACK_PAYMENT_PAID", "CASHBACK", "CashbackPayment",
                        payment.getId(), reference, "SUCCESS",
                        "Transaction: " + safe(payment.getTransactionRef()) + " | Carte: " + safe(payment.getMaskedCard())
                                + " | Commerçant: " + merchantName(payment.getMerchantId()) + " | Montant cashback: " + payment.getAmount() + " " + safe(payment.getCurrency())
                                + " | Campagne: " + campaignName(payment.getCampaignId()) + " | Statut: PENDING -> SUCCESS");
            } catch (RuntimeException ex) {
                summary.failed++;
                summary.errors.add(ex.getMessage() == null ? "Paiement impossible" : ex.getMessage());
            }
        }
        auditLogService.log("PROCESS_PENDING_CASHBACK_PAYMENTS", "CASHBACK", "Batch", null,
                batchReference, summary.failed == 0 ? "SUCCESS" : "PARTIAL_FAILURE",
                "Paiements en attente: " + summary.totalPending + " | Traités: " + summary.processed
                        + " | Échecs: " + summary.failed + " | Montant total: " + summary.totalAmount + " FCFA");
        return summary;
    }

    private String safe(String value) { return value == null || value.isBlank() ? "—" : value; }
    private String merchantName(Long id) { return id == null ? "—" : merchantRepository.findById(id).map(m -> safe(m.getName())).orElse("—"); }
    private String campaignName(Long id) { return id == null ? "—" : campaignRepository.findById(id).map(c -> safe(c.getName())).orElse("—"); }

    public static class ProcessingSummary {
        private int totalPending;
        private int processed;
        private int skipped;
        private int failed;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private final List<String> paymentReferences = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        public int getTotalPending() { return totalPending; }
        public int getProcessed() { return processed; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public List<String> getPaymentReferences() { return paymentReferences; }
        public List<String> getErrors() { return errors; }
    }
}
