package com.afriland.ticket2cash.cashback;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.pos.PosTransaction;
import com.afriland.ticket2cash.pos.PosTransactionRepository;
import com.afriland.ticket2cash.pos.TransactionWorkflowStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CashbackCreditService {
    private final CashbackPaymentRepository paymentRepository;
    private final PosTransactionRepository transactionRepository;
    private final AuditLogService auditLogService;

    public CashbackCreditService(CashbackPaymentRepository paymentRepository, PosTransactionRepository transactionRepository, AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository; this.transactionRepository = transactionRepository; this.auditLogService = auditLogService;
    }

    @Transactional
    public CreditSummary creditPending(HttpServletRequest request) {
        String actor = approver(request);
        CreditSummary summary = new CreditSummary();
        List<CashbackPayment> payments = paymentRepository.findByCreditStatusOrderByIdAsc(CashbackCreditStatus.CREDIT_PENDING);
        summary.totalEligible = payments.size();
        auditLogService.log("CREDIT_CASHBACK_BATCH_STARTED", "CASHBACK", "CreditBatch", null, actor, "SUCCESS", "Pending cashback credits: " + payments.size());
        for (CashbackPayment payment : payments) processOne(payment, actor, summary);
        return summary;
    }

    @Transactional
    public CreditSummary creditOne(Long id, HttpServletRequest request) {
        String actor = approver(request); CreditSummary summary = new CreditSummary(); summary.totalEligible = 1;
        CashbackPayment payment = paymentRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));
        processOne(payment, actor, summary); return summary;
    }

    private void processOne(CashbackPayment payment, String actor, CreditSummary summary) {
        if (payment.getCreditStatus() == CashbackCreditStatus.CREDITED) { summary.skipped++; return; }
        PosTransaction transaction = payment.getTransactionRef() == null ? null : transactionRepository.findByTransactionRef(payment.getTransactionRef()).orElse(null);
        String reason = null;
        if (transaction == null) reason = "Transaction liée introuvable";
        else if (!(transaction.getWorkflowStatus() == TransactionWorkflowStatus.APPROVED_FOR_CREDIT || "APPROVED_FOR_CREDIT".equalsIgnoreCase(transaction.getCurrentStep()))) reason = "Transaction non approuvée pour crédit";
        else if (payment.getAmount() == null || payment.getAmount().signum() <= 0) reason = "Montant cashback invalide";
        else if ((transaction.getMaskedCard() == null || transaction.getMaskedCard().isBlank()) && (transaction.getCardHash() == null || transaction.getCardHash().isBlank())) reason = "Référence carte absente";
        if (reason != null) { summary.skipped++; auditLogService.log("CASHBACK_CREDIT_SKIPPED", "CASHBACK", "CashbackPayment", payment.getId(), actor, "SKIPPED", details(payment, reason, null)); return; }
        try {
            payment.setCreditStatus(CashbackCreditStatus.PROCESSING); paymentRepository.save(payment);
            String reference = "CREDIT-TXN-" + payment.getTransactionRef() + "-" + payment.getId();
            payment.setCreditReference(reference); payment.setCreditedAt(LocalDateTime.now()); payment.setCreditStatus(CashbackCreditStatus.CREDITED);
            payment.setPrepaidAccountRef(transaction.getCardHash()); payment.setCustomerRef(transaction.getCardHash()); paymentRepository.save(payment);
            summary.credited++; summary.totalAmount = summary.totalAmount.add(payment.getAmount()); summary.creditReferences.add(reference);
            auditLogService.log("CASHBACK_CREDITED", "CASHBACK", "CashbackPayment", payment.getId(), actor, "SUCCESS", details(payment, null, reference));
        } catch (RuntimeException ex) { payment.setCreditStatus(CashbackCreditStatus.FAILED); payment.setCreditFailureReason(ex.getMessage()); paymentRepository.save(payment); summary.failed++; summary.errors.add("Paiement " + payment.getId() + ": " + ex.getMessage()); auditLogService.log("CASHBACK_CREDIT_FAILED", "CASHBACK", "CashbackPayment", payment.getId(), actor, "FAILED", details(payment, ex.getMessage(), null)); }
    }

    private String details(CashbackPayment p, String reason, String ref) { return "transactionRef=" + safe(p.getTransactionRef()) + " | maskedCard=" + safe(p.getMaskedCard()) + " | amount=" + p.getAmount() + " | creditReference=" + safe(ref == null ? p.getCreditReference() : ref) + (reason == null ? "" : " | reason=" + safe(reason)); }
    private String safe(String s) { return s == null || s.isBlank() ? "—" : s.replaceAll("[\\r\\n]", " "); }
    private String approver(HttpServletRequest request) { HttpSession s=request==null?null:request.getSession(false); String role=s==null?null:String.valueOf(s.getAttribute("AUTH_ROLE")); if (!("ADMIN".equals(role)||"SUPERVISEUR".equals(role))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Rôle ADMIN ou SUPERVISEUR requis"); return String.valueOf(s.getAttribute("AUTH_USERNAME")); }

    public static class CreditSummary { private int totalEligible,credited,skipped,failed; private BigDecimal totalAmount=BigDecimal.ZERO; private final List<String> creditReferences=new ArrayList<>(),errors=new ArrayList<>(); public int getTotalEligible(){return totalEligible;} public int getCredited(){return credited;} public int getSkipped(){return skipped;} public int getFailed(){return failed;} public BigDecimal getTotalAmount(){return totalAmount;} public List<String> getCreditReferences(){return creditReferences;} public List<String> getErrors(){return errors;} }
}
