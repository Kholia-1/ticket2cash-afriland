package com.afriland.ticket2cash.pos;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.cashback.CashbackPaymentStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransactionWorkflowService {
    private final PosTransactionRepository repository;
    private final AuditLogService auditLogService;
    private final CashbackPaymentRepository cashbackPaymentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public TransactionWorkflowService(PosTransactionRepository repository, AuditLogService auditLogService,
                                      CashbackPaymentRepository cashbackPaymentRepository) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.cashbackPaymentRepository = cashbackPaymentRepository;
    }

    /** Kept for focused unit tests and older callers that do not exercise the batch operation. */
    public TransactionWorkflowService(PosTransactionRepository repository, AuditLogService auditLogService) {
        this(repository, auditLogService, null);
    }

    @Transactional
    public Map<String, Object> validateCashbackBeforePayment(HttpServletRequest request) {
        String actor = requireApprover(request);
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int totalChecked = 0, approved = 0, manualReview = 0, rejected = 0, skipped = 0;
        auditLogService.log("CASHBACK_PREPAYMENT_CHECK_STARTED", "CASHBACK", "Transaction", null, actor, "STARTED",
                "Contrôle cashback avant paiement démarré");

        List<PosTransaction> transactions = repository.findAllByOrderByReceivedAtDesc();
        for (PosTransaction tx : transactions) {
            TransactionWorkflowStatus current = tx.getWorkflowStatus();
            if (current == null) current = TransactionWorkflowStatus.RECEIVED;
            if (current == TransactionWorkflowStatus.APPROVED_FOR_PAYMENT
                    || current == TransactionWorkflowStatus.APPROVED_FOR_CREDIT
                    || current == TransactionWorkflowStatus.CREDITED
                    || current == TransactionWorkflowStatus.PAYMENT_GENERATED) {
                skipped++;
                continue;
            }
            if (current == TransactionWorkflowStatus.REJECTED) {
                skipped++;
                continue;
            }
            totalChecked++;
            try {
                if (current == TransactionWorkflowStatus.MANUAL_REVIEW || tx.isManualReviewRequired()) {
                    manualReview++;
                    auditBatch(tx, "CASHBACK_PREPAYMENT_REVIEW_REQUIRED", actor, "Révision manuelle déjà requise");
                    continue;
                }
                String invalid = invalidReason(tx);
                CashbackPayment payment = null;
                if (invalid == null && cashbackPaymentRepository != null)
                    payment = cashbackPaymentRepository.findByTransactionRef(tx.getTransactionRef()).orElse(null);
                if (invalid == null && payment == null) invalid = "Cashback non calculé ou paiement introuvable";
                if (invalid == null && !isPositive(payment.getFinalCashbackAmount() == null ? payment.getAmount() : payment.getFinalCashbackAmount()))
                    invalid = "Montant cashback invalide";
                if (invalid != null) {
                    apply(tx, TransactionWorkflowStatus.REJECTED, actor, invalid, true);
                    tx.setRejectionReason(invalid);
                    repository.save(tx);
                    rejected++;
                    auditBatch(tx, "CASHBACK_PREPAYMENT_REJECTED", actor, invalid, payment);
                    continue;
                }
                apply(tx, TransactionWorkflowStatus.APPROVED_FOR_PAYMENT, actor, "Contrôle cashback avant paiement validé", false);
                repository.save(tx);
                approved++;
                auditBatch(tx, "CASHBACK_PREPAYMENT_APPROVED", actor, "Prête pour paiement", payment);
            } catch (RuntimeException ex) {
                errors.add(tx.getTransactionRef() + ": " + safe(ex.getMessage()));
            }
        }
        result.put("totalChecked", totalChecked);
        result.put("approvedForPayment", approved);
        result.put("manualReview", manualReview);
        result.put("rejected", rejected);
        result.put("skipped", skipped);
        result.put("errors", errors);
        auditLogService.log("CASHBACK_PREPAYMENT_CHECK_COMPLETED", "CASHBACK", "Transaction", null, actor, "SUCCESS",
                "totalChecked=" + totalChecked + " | approvedForPayment=" + approved + " | manualReview=" + manualReview
                        + " | rejected=" + rejected + " | skipped=" + skipped);
        return result;
    }

    private String invalidReason(PosTransaction tx) {
        if (tx.getAmount() == null || tx.getAmount().signum() <= 0) return "Montant transaction invalide";
        if ((tx.getCardHash() == null || tx.getCardHash().isBlank())
                && (tx.getMaskedCard() == null || tx.getMaskedCard().isBlank())) return "Carte non vérifiable";
        if (tx.getStatus() != null && "FAILED".equalsIgnoreCase(tx.getStatus())) return "Transaction en échec";
        return null;
    }

    private boolean isPositive(java.math.BigDecimal value) { return value != null && value.signum() > 0; }

    private void auditBatch(PosTransaction tx, String action, String actor, String reason) {
        auditBatch(tx, action, actor, reason, null);
    }

    private void auditBatch(PosTransaction tx, String action, String actor, String reason, CashbackPayment payment) {
        String details = "transactionRef=" + safe(tx.getTransactionRef()) + " | maskedCard=" + safe(tx.getMaskedCard())
                + " | merchantId=" + safe(tx.getMerchantId()) + " | campaignId=" + safe(payment == null ? null : payment.getCampaignId())
                + " | campaignCashbackAmount=" + safe(payment == null ? null : payment.getCampaignCashbackAmount())
                + " | loyaltyBonusAmount=" + safe(payment == null ? null : payment.getLoyaltyBonusAmount())
                + " | finalCashbackAmount=" + safe(payment == null ? null : payment.getFinalCashbackAmount())
                + (reason == null ? "" : " | reason=" + safe(reason));
        auditLogService.log(action, "CASHBACK", "PosTransaction", tx.getId(), actor, "SUCCESS", details);
    }

    @Transactional(readOnly = true)
    public WorkflowView view(Long id, HttpServletRequest request) {
        PosTransaction tx = find(id);
        ensureCanView(tx, request);
        return toView(tx);
    }

    @Transactional
    public WorkflowView validateStep(Long id, String step, String comment, HttpServletRequest request) {
        String actor = requireApprover(request);
        TransactionWorkflowStatus status = parseStatus(step);
        PosTransaction tx = find(id);
        ensureStepTransition(tx, status);
        apply(tx, status, actor, comment, false);
        PosTransaction saved = repository.save(tx);
        audit(saved, "TRANSACTION_STEP_VALIDATED", status.name(), actor, comment);
        return toView(saved);
    }

    @Transactional
    public WorkflowView manualReview(Long id, String comment, HttpServletRequest request) {
        String actor = requireReviewer(request);
        PosTransaction tx = find(id);
        if (workflowStatus(tx) == TransactionWorkflowStatus.CREDITED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une transaction déjà créditée ne peut plus être mise en révision");
        apply(tx, TransactionWorkflowStatus.MANUAL_REVIEW, actor, comment, true);
        PosTransaction saved = repository.save(tx);
        audit(saved, "TRANSACTION_MANUAL_REVIEW", "MANUAL_REVIEW", actor, comment);
        return toView(saved);
    }

    @Transactional
    public WorkflowView reject(Long id, String reason, HttpServletRequest request) {
        String actor = requireApprover(request);
        PosTransaction tx = find(id);
        if (workflowStatus(tx) == TransactionWorkflowStatus.CREDITED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une transaction déjà créditée ne peut plus être rejetée");
        apply(tx, TransactionWorkflowStatus.REJECTED, actor, reason, true);
        tx.setRejectionReason(trim(reason));
        PosTransaction saved = repository.save(tx);
        audit(saved, "TRANSACTION_REJECTED", "REJECTED", actor, reason);
        return toView(saved);
    }

    @Transactional
    public WorkflowView approveForPayment(Long id, String comment, HttpServletRequest request) {
        String actor = requireApprover(request);
        PosTransaction tx = find(id);
        TransactionWorkflowStatus current = workflowStatus(tx);
        if (!(current == TransactionWorkflowStatus.RECEIVED
                || current == TransactionWorkflowStatus.DEDUPLICATED
                || current == TransactionWorkflowStatus.CARD_VALIDATED
                || current == TransactionWorkflowStatus.MERCHANT_MATCHED
                || current == TransactionWorkflowStatus.CAMPAIGN_MATCHED
                || current == TransactionWorkflowStatus.CASHBACK_CALCULATED
                || current == TransactionWorkflowStatus.FRAUD_CHECKED
                || current == TransactionWorkflowStatus.MANUAL_REVIEW
                || current == TransactionWorkflowStatus.APPROVED_FOR_PAYMENT)
                || current == TransactionWorkflowStatus.APPROVED_FOR_CREDIT
                || current == TransactionWorkflowStatus.CREDIT_PENDING
                || current == TransactionWorkflowStatus.CREDITED
                || current == TransactionWorkflowStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction non éligible à l'autorisation de paiement");
        }
        apply(tx, TransactionWorkflowStatus.APPROVED_FOR_PAYMENT, actor, comment, false);
        PosTransaction saved = repository.save(tx);
        audit(saved, "TRANSACTION_APPROVED_FOR_PAYMENT", "APPROVED_FOR_PAYMENT", actor, comment);
        return toView(saved);
    }

    @Transactional
    public WorkflowView approveForCredit(Long id, String comment, HttpServletRequest request) {
        String actor = requireApprover(request);
        PosTransaction tx = find(id);
        TransactionWorkflowStatus current = workflowStatus(tx);
        if (current != TransactionWorkflowStatus.APPROVED_FOR_PAYMENT
                && current != TransactionWorkflowStatus.PAYMENT_GENERATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le paiement doit être autorisé ou généré avant le crédit client");
        }
        apply(tx, TransactionWorkflowStatus.APPROVED_FOR_CREDIT, actor, comment, false);
        PosTransaction saved = repository.save(tx);
        audit(saved, "TRANSACTION_APPROVED_FOR_CREDIT", "APPROVED_FOR_CREDIT", actor, comment);
        return toView(saved);
    }

    private void apply(PosTransaction tx, TransactionWorkflowStatus status, String actor, String comment, boolean review) {
        tx.setWorkflowStatus(status);
        tx.setCurrentStep(status.name());
        tx.setManualReviewRequired(review);
        tx.setValidatedBy(actor);
        tx.setValidatedAt(LocalDateTime.now());
        tx.setLastWorkflowComment(trim(comment));
        if (!review) tx.setRejectionReason(null);
    }

    private TransactionWorkflowStatus workflowStatus(PosTransaction tx) {
        return tx.getWorkflowStatus() == null ? TransactionWorkflowStatus.RECEIVED : tx.getWorkflowStatus();
    }

    /**
     * Prevents accidental actions on terminal transactions and prevents the
     * generic validation endpoint from jumping beyond the payment/credit gates.
     * Normal imported transactions may still be validated progressively from
     * RECEIVED, preserving compatibility with the existing UI.
     */
    private void ensureStepTransition(PosTransaction tx, TransactionWorkflowStatus target) {
        TransactionWorkflowStatus current = workflowStatus(tx);
        if (current == TransactionWorkflowStatus.REJECTED || current == TransactionWorkflowStatus.CREDITED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction déjà clôturée");
        }
        if (target == TransactionWorkflowStatus.APPROVED_FOR_CREDIT
                && current != TransactionWorkflowStatus.APPROVED_FOR_PAYMENT
                && current != TransactionWorkflowStatus.PAYMENT_GENERATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le paiement doit être autorisé ou généré avant le crédit client");
        }
        if (target == TransactionWorkflowStatus.CREDITED
                && current != TransactionWorkflowStatus.APPROVED_FOR_CREDIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La transaction doit être autorisée pour crédit avant le crédit client");
        }
    }

    private TransactionWorkflowStatus parseStatus(String step) {
        if (step == null || step.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "step obligatoire");
        try { return TransactionWorkflowStatus.valueOf(step.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Étape de workflow inconnue"); }
    }

    private PosTransaction find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));
    }

    private String requireAuthenticated(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        if (session == null || session.getAttribute("AUTH_USER_ID") == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise");
        return String.valueOf(session.getAttribute("AUTH_USERNAME"));
    }

    private String requireApprover(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        String role = session == null ? null : String.valueOf(session.getAttribute("AUTH_ROLE"));
        if (!("ADMIN".equals(role) || "SUPERVISEUR".equals(role)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle ADMIN ou SUPERVISEUR requis");
        return requireAuthenticated(request);
    }

    private String requireReviewer(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        String role = session == null ? null : String.valueOf(session.getAttribute("AUTH_ROLE"));
        if (!("ADMIN".equals(role) || "SUPERVISEUR".equals(role) || "OPERATEUR".equals(role)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle OPERATEUR, ADMIN ou SUPERVISEUR requis");
        return requireAuthenticated(request);
    }

    private void ensureCanView(PosTransaction tx, HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        String role = session == null ? null : String.valueOf(session.getAttribute("AUTH_ROLE"));
        if (session == null || session.getAttribute("AUTH_USER_ID") == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise");
        if ("PARTNER".equals(role)) {
            Object merchant = session.getAttribute("AUTH_MERCHANT_ID");
            Long merchantId = merchant == null ? null : Long.valueOf(String.valueOf(merchant));
            if (merchantId == null || !merchantId.equals(tx.getMerchantId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction hors périmètre partenaire");
        }
    }

    private void audit(PosTransaction tx, String action, String step, String actor, String comment) {
        String details = "transactionRef=" + tx.getTransactionRef() + " | maskedCard=" + safe(tx.getMaskedCard())
                + " | step=" + step + " | action=" + action + " | actor=" + actor + " | comment=" + safe(comment);
        auditLogService.log(action, "POS", "PosTransaction", tx.getId(), actor, "SUCCESS", details);
    }

    private String safe(String value) { return value == null || value.isBlank() ? "—" : value.replaceAll("[\\r\\n]", " "); }
    private String safe(Object value) { return value == null ? "—" : safe(String.valueOf(value)); }
    private String trim(String value) { return value == null ? null : value.trim(); }

    private WorkflowView toView(PosTransaction tx) {
        TransactionWorkflowStatus current = tx.getWorkflowStatus() == null ? TransactionWorkflowStatus.RECEIVED : tx.getWorkflowStatus();
        List<Map<String, Object>> timeline = new ArrayList<>();
        boolean reached = false;
        for (TransactionWorkflowStatus status : Arrays.asList(
                TransactionWorkflowStatus.RECEIVED, TransactionWorkflowStatus.CARD_VALIDATED,
                TransactionWorkflowStatus.MERCHANT_MATCHED, TransactionWorkflowStatus.CAMPAIGN_MATCHED,
                TransactionWorkflowStatus.CASHBACK_CALCULATED, TransactionWorkflowStatus.FRAUD_CHECKED,
                TransactionWorkflowStatus.APPROVED_FOR_PAYMENT, TransactionWorkflowStatus.PAYMENT_GENERATED,
                TransactionWorkflowStatus.APPROVED_FOR_CREDIT, TransactionWorkflowStatus.CREDIT_PENDING,
                TransactionWorkflowStatus.CREDITED)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("step", status.name());
            item.put("status", status == current ? "CURRENT" : (!reached ? "COMPLETED" : "PENDING"));
            item.put("date", status == current ? tx.getValidatedAt() : (status == TransactionWorkflowStatus.RECEIVED ? tx.getReceivedAt() : null));
            item.put("validatedBy", status == current ? tx.getValidatedBy() : null);
            item.put("comment", status == current ? tx.getLastWorkflowComment() : null);
            timeline.add(item);
            if (status == current) reached = true;
        }
        if (current == TransactionWorkflowStatus.MANUAL_REVIEW || current == TransactionWorkflowStatus.REJECTED) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("step", current.name()); item.put("status", "CURRENT"); item.put("date", tx.getValidatedAt());
            item.put("validatedBy", tx.getValidatedBy()); item.put("comment", tx.getLastWorkflowComment()); timeline.add(item);
        }
        return new WorkflowView(tx, current, timeline);
    }

    public static class WorkflowView {
        private final Long id; private final String transactionRef; private final String maskedCard;
        private final TransactionWorkflowStatus workflowStatus; private final String currentStep;
        private final boolean manualReviewRequired; private final String validatedBy;
        private final LocalDateTime validatedAt; private final String rejectionReason;
        private final String lastWorkflowComment; private final List<Map<String, Object>> timeline;
        WorkflowView(PosTransaction tx, TransactionWorkflowStatus current, List<Map<String, Object>> timeline) {
            id=tx.getId(); transactionRef=tx.getTransactionRef(); maskedCard=tx.getMaskedCard(); workflowStatus=current;
            currentStep=tx.getCurrentStep()==null?current.name():tx.getCurrentStep(); manualReviewRequired=tx.isManualReviewRequired();
            validatedBy=tx.getValidatedBy(); validatedAt=tx.getValidatedAt(); rejectionReason=tx.getRejectionReason();
            lastWorkflowComment=tx.getLastWorkflowComment(); this.timeline=timeline;
        }
        public Long getId(){return id;} public String getTransactionRef(){return transactionRef;} public String getMaskedCard(){return maskedCard;}
        public TransactionWorkflowStatus getWorkflowStatus(){return workflowStatus;} public String getCurrentStep(){return currentStep;}
        public boolean isManualReviewRequired(){return manualReviewRequired;} public String getValidatedBy(){return validatedBy;}
        public LocalDateTime getValidatedAt(){return validatedAt;} public String getRejectionReason(){return rejectionReason;}
        public String getLastWorkflowComment(){return lastWorkflowComment;} public List<Map<String,Object>> getTimeline(){return timeline;}
    }
}
