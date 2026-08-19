package com.afriland.ticket2cash.cashback;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.pos.PosTransaction;
import com.afriland.ticket2cash.pos.PosTransactionRepository;
import com.afriland.ticket2cash.pos.TransactionWorkflowStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashbackCreditServiceTest {
    @Mock CashbackPaymentRepository paymentRepository;
    @Mock PosTransactionRepository transactionRepository;
    @Mock AuditLogService auditLogService;
    @Mock HttpServletRequest request;
    @Mock HttpSession session;

    @Test
    void approvedTransactionIsCreditedIdempotently() {
        CashbackPayment payment = payment(10L, CashbackCreditStatus.CREDIT_PENDING);
        PosTransaction transaction = transaction("TX-10", TransactionWorkflowStatus.APPROVED_FOR_CREDIT);
        authorized();
        when(paymentRepository.findByCreditStatusOrderByIdAsc(CashbackCreditStatus.CREDIT_PENDING)).thenReturn(List.of(payment));
        when(transactionRepository.findByTransactionRef("TX-10")).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(CashbackPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashbackCreditService.CreditSummary summary = service().creditPending(request);

        assertEquals(1, summary.getCredited());
        assertEquals(new BigDecimal("1250"), summary.getTotalAmount());
        assertEquals(CashbackCreditStatus.CREDITED, payment.getCreditStatus());
        assertNotNull(payment.getCreditReference());
        assertNotNull(payment.getCreditedAt());
        verify(auditLogService).log(eq("CASHBACK_CREDITED"), anyString(), anyString(), eq(10L), eq("admin"), eq("SUCCESS"), contains("transactionRef=TX-10"));
    }

    @Test
    void transactionNotApprovedIsSkipped() {
        CashbackPayment payment = payment(11L, CashbackCreditStatus.CREDIT_PENDING);
        authorized();
        when(paymentRepository.findByCreditStatusOrderByIdAsc(CashbackCreditStatus.CREDIT_PENDING)).thenReturn(List.of(payment));
        when(transactionRepository.findByTransactionRef("TX-11")).thenReturn(Optional.of(transaction("TX-11", TransactionWorkflowStatus.CASHBACK_CALCULATED)));

        CashbackCreditService.CreditSummary summary = service().creditPending(request);

        assertEquals(0, summary.getCredited());
        assertEquals(1, summary.getSkipped());
        verify(paymentRepository, never()).save(any());
        verify(auditLogService).log(eq("CASHBACK_CREDIT_SKIPPED"), anyString(), anyString(), eq(11L), eq("admin"), eq("SKIPPED"), contains("non approuv"));
    }

    @Test
    void unprocessedPaymentIsSkippedWithExplicitReason() {
        CashbackPayment payment = payment(15L, CashbackCreditStatus.CREDIT_PENDING);
        payment.setStatus(CashbackPaymentStatus.PENDING);
        authorized();
        when(paymentRepository.findByCreditStatusOrderByIdAsc(CashbackCreditStatus.CREDIT_PENDING)).thenReturn(List.of(payment));
        CashbackCreditService.CreditSummary summary = service().creditPending(request);
        assertEquals(0, summary.getCredited());
        assertEquals(1, summary.getSkipped());
        verify(auditLogService).log(eq("CASHBACK_CREDIT_SKIPPED"), anyString(), anyString(), eq(15L), eq("admin"), eq("SKIPPED"), contains("non traité"));
    }

    @Test
    void invalidAmountAndMissingTransactionAreSkipped() {
        CashbackPayment invalid = payment(12L, CashbackCreditStatus.CREDIT_PENDING);
        invalid.setAmount(BigDecimal.ZERO);
        CashbackPayment missing = payment(13L, CashbackCreditStatus.CREDIT_PENDING);
        authorized();
        when(paymentRepository.findByCreditStatusOrderByIdAsc(CashbackCreditStatus.CREDIT_PENDING)).thenReturn(List.of(invalid, missing));
        when(transactionRepository.findByTransactionRef("TX-12")).thenReturn(Optional.of(transaction("TX-12", TransactionWorkflowStatus.APPROVED_FOR_CREDIT)));
        when(transactionRepository.findByTransactionRef("TX-13")).thenReturn(Optional.empty());

        CashbackCreditService.CreditSummary summary = service().creditPending(request);

        assertEquals(0, summary.getCredited());
        assertEquals(2, summary.getSkipped());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void alreadyCreditedPaymentIsNotProcessedAgain() {
        CashbackPayment payment = payment(14L, CashbackCreditStatus.CREDITED);
        authorized();
        when(paymentRepository.findById(14L)).thenReturn(Optional.of(payment));

        CashbackCreditService.CreditSummary summary = service().creditOne(14L, request);

        assertEquals(1, summary.getSkipped());
        verify(transactionRepository, never()).findByTransactionRef(anyString());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void nonPrivilegedRoleIsRejected() {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_ROLE")).thenReturn("OPERATEUR");
        assertThrows(ResponseStatusException.class, () -> service().creditPending(request));
        verifyNoInteractions(paymentRepository);
    }

    private CashbackCreditService service() { return new CashbackCreditService(paymentRepository, transactionRepository, auditLogService); }

    private void authorized() {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_ROLE")).thenReturn("ADMIN");
        when(session.getAttribute("AUTH_USERNAME")).thenReturn("admin");
    }

    private CashbackPayment payment(Long id, CashbackCreditStatus status) {
        CashbackPayment p = new CashbackPayment(); p.setId(id); p.setCreditStatus(status); p.setStatus(CashbackPaymentStatus.SUCCESS); p.setAmount(new BigDecimal("1250")); p.setCurrency("FCFA"); p.setTransactionRef("TX-" + id); p.setMaskedCard("****1234"); return p;
    }

    private PosTransaction transaction(String ref, TransactionWorkflowStatus status) {
        PosTransaction t = new PosTransaction(); t.setTransactionRef(ref);
        t.setWorkflowStatus(status); t.setCurrentStep(status.name()); t.setCardHash("HASH_DEMO"); t.setMaskedCard("****1234");
        return t;
    }
}
