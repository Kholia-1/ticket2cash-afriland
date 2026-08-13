package com.afriland.ticket2cash.pos;

import com.afriland.ticket2cash.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TransactionWorkflowServiceTest {
    @Mock PosTransactionRepository repository;
    @Mock AuditLogService auditLogService;
    @Mock HttpServletRequest request;
    @Mock HttpSession session;
    private TransactionWorkflowService service;
    private PosTransaction transaction;

    @BeforeEach
    void setUp() {
        service = new TransactionWorkflowService(repository, auditLogService);
        transaction = new PosTransaction();
        transaction.setId(7L);
        transaction.setTransactionRef("TX-WF-001");
        transaction.setMaskedCard("****1234");
        transaction.setMerchantId(10L);
        lenient().when(repository.findById(7L)).thenReturn(Optional.of(transaction));
        lenient().when(repository.save(any(PosTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(request.getSession(false)).thenReturn(session);
        lenient().when(session.getAttribute("AUTH_USER_ID")).thenReturn(1L);
        lenient().when(session.getAttribute("AUTH_USERNAME")).thenReturn("supervisor");
    }

    @Test
    void validatesStepAndWritesAudit() {
        when(session.getAttribute("AUTH_ROLE")).thenReturn("SUPERVISEUR");
        var result = service.validateStep(7L, "CARD_VALIDATED", "Carte contrôlée", request);
        assertEquals(TransactionWorkflowStatus.CARD_VALIDATED, transaction.getWorkflowStatus());
        assertEquals("supervisor", transaction.getValidatedBy());
        assertEquals(TransactionWorkflowStatus.CARD_VALIDATED, result.getWorkflowStatus());
        verify(auditLogService).log("TRANSACTION_STEP_VALIDATED", "POS", "PosTransaction", 7L,
                "supervisor", "SUCCESS", "transactionRef=TX-WF-001 | maskedCard=****1234 | step=CARD_VALIDATED | action=TRANSACTION_STEP_VALIDATED | actor=supervisor | comment=Carte contrôlée");
    }

    @Test
    void manualReviewAndRejectAreRecorded() {
        when(session.getAttribute("AUTH_ROLE")).thenReturn("OPERATEUR");
        service.manualReview(7L, "Score élevé", request);
        assertEquals(TransactionWorkflowStatus.MANUAL_REVIEW, transaction.getWorkflowStatus());
        assertEquals("Score élevé", transaction.getLastWorkflowComment());

        when(session.getAttribute("AUTH_ROLE")).thenReturn("ADMIN");
        service.reject(7L, "Fraude confirmée", request);
        assertEquals(TransactionWorkflowStatus.REJECTED, transaction.getWorkflowStatus());
        assertEquals("Fraude confirmée", transaction.getRejectionReason());
        verify(auditLogService).log("TRANSACTION_REJECTED", "POS", "PosTransaction", 7L,
                "supervisor", "SUCCESS", "transactionRef=TX-WF-001 | maskedCard=****1234 | step=REJECTED | action=TRANSACTION_REJECTED | actor=supervisor | comment=Fraude confirmée");
    }

    @Test
    void paymentAndCreditApprovalsAdvanceWorkflow() {
        when(session.getAttribute("AUTH_ROLE")).thenReturn("ADMIN");
        service.approveForPayment(7L, "OK paiement", request);
        assertEquals(TransactionWorkflowStatus.APPROVED_FOR_PAYMENT, transaction.getWorkflowStatus());
        service.approveForCredit(7L, "OK crédit", request);
        assertEquals(TransactionWorkflowStatus.APPROVED_FOR_CREDIT, transaction.getWorkflowStatus());
    }

    @Test
    void unauthorizedRoleCannotValidate() {
        when(session.getAttribute("AUTH_ROLE")).thenReturn("OPERATEUR");
        assertThrows(ResponseStatusException.class,
                () -> service.validateStep(7L, "CARD_VALIDATED", null, request));
    }

    @Test
    void legacyTransactionWithoutStatusIsReadableAsReceived() {
        when(session.getAttribute("AUTH_ROLE")).thenReturn("ADMIN");
        var result = service.view(7L, request);
        assertEquals(TransactionWorkflowStatus.RECEIVED, result.getWorkflowStatus());
        assertEquals("Non initialisé", result.getTimeline().stream()
                .filter(step -> "RECEIVED".equals(step.get("step")))
                .findFirst().map(step -> "Non initialisé").orElse(""));
    }
}
