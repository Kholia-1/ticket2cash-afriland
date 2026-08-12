package com.afriland.ticket2cash.cashback;

import com.afriland.ticket2cash.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashbackPaymentProcessingServiceTest {
    @Mock CashbackPaymentRepository repository;
    @Mock AuditLogService auditLogService;

    @Test
    void pendingPaymentBecomesPaidAndSummaryIsCorrect() {
        CashbackPayment pending = payment(CashbackPaymentStatus.PENDING, "1000");
        when(repository.findByStatusOrderByIdAsc(CashbackPaymentStatus.PENDING)).thenReturn(List.of(pending));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        CashbackPaymentProcessingService.ProcessingSummary result =
                new CashbackPaymentProcessingService(repository, auditLogService).processPending();

        assertEquals(CashbackPaymentStatus.SUCCESS, pending.getStatus());
        assertEquals(1, result.getProcessed());
        assertEquals(new BigDecimal("1000"), result.getTotalAmount());
        verify(repository).save(pending);
    }

    @Test
    void alreadyPaidPaymentIsNotQueriedForProcessing() {
        when(repository.findByStatusOrderByIdAsc(CashbackPaymentStatus.PENDING)).thenReturn(List.of());
        CashbackPaymentProcessingService.ProcessingSummary result =
                new CashbackPaymentProcessingService(repository, auditLogService).processPending();
        assertEquals(0, result.getProcessed());
        verify(repository, never()).save(any());
    }

    private CashbackPayment payment(CashbackPaymentStatus status, String amount) {
        CashbackPayment payment = new CashbackPayment();
        payment.setStatus(status); payment.setAmount(new BigDecimal(amount));
        payment.setTransactionRef("TX-" + amount);
        return payment;
    }
}
