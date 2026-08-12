package com.afriland.ticket2cash.pos;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.cashback.engine.CashbackDecision;
import com.afriland.ticket2cash.cashback.engine.CashbackDecisionCode;
import com.afriland.ticket2cash.cashback.engine.TransactionCashbackProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    @Mock PosTransactionRepository repository;
    @Mock TransactionCashbackProcessingService cashbackService;
    @Mock AuditLogService auditLogService;
    private TransactionImportService service;

    @BeforeEach
    void setUp() { service = new TransactionImportService(repository, cashbackService, auditLogService); }

    @Test
    void validTransactionIsInsertedAndProcessed() throws Exception {
        when(repository.existsByTransactionRef("TX-1")).thenReturn(false);
        when(repository.saveAndFlush(any(PosTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashbackService.process(any())).thenReturn(approved());

        TransactionImportService.ImportSummary result = service.importCsv(csv(
                "TX-1,2026-08-12T10:25:00,hash,****1234,456120,7,Shop,25000,,5411,F2F,T1,"
        ));

        assertEquals(1, result.getInserted());
        assertEquals(1, result.getCashbackProcessed());
        assertEquals(1, result.getCashbackApproved());
        verify(repository).saveAndFlush(any(PosTransaction.class));
    }

    @Test
    void duplicateReferenceIsIgnored() throws Exception {
        when(repository.existsByTransactionRef("TX-1")).thenReturn(true);

        TransactionImportService.ImportSummary result = service.importCsv(csv(
                "TX-1,2026-08-12T10:25:00,hash,****1234,456120,7,Shop,25000,FCFA,5411,F2F,T1,SUCCESS"
        ));

        assertEquals(0, result.getInserted());
        assertEquals(1, result.getDuplicates());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(cashbackService);
    }

    @Test
    void missingReferenceIsRejected() throws Exception {
        TransactionImportService.ImportSummary result = service.importCsv(csv(
                ",2026-08-12T10:25:00,hash,****1234,456120,7,Shop,25000,FCFA,5411,F2F,T1,SUCCESS"
        ));

        assertEquals(1, result.getInvalidRows());
        assertEquals(0, result.getInserted());
    }

    @Test
    void invalidAmountIsRejected() throws Exception {
        TransactionImportService.ImportSummary result = service.importCsv(csv(
                "TX-1,2026-08-12T10:25:00,hash,****1234,456120,7,Shop,nope,FCFA,5411,F2F,T1,SUCCESS"
        ));

        assertEquals(1, result.getInvalidRows());
        assertEquals(0, result.getInserted());
    }

    @Test
    void summaryCountsRowsDuplicatesInvalidAndCashback() throws Exception {
        when(repository.existsByTransactionRef("TX-1")).thenReturn(false);
        when(repository.existsByTransactionRef("TX-2")).thenReturn(true);
        when(repository.saveAndFlush(any(PosTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashbackService.process(any())).thenReturn(approved());

        TransactionImportService.ImportSummary result = service.importCsv(csv(
                "TX-1,2026-08-12T10:25:00,hash,****1234,456120,7,Shop,25000,FCFA,5411,F2F,T1,SUCCESS\n"
                        + "TX-2,2026-08-12T10:25:00,hash,****1234,456120,7,Shop,25000,FCFA,5411,F2F,T1,SUCCESS\n"
                        + "TX-3,2026-08-12T10:25:00,hash,****1234,456120,7,Shop,-2,FCFA,5411,F2F,T1,SUCCESS"
        ));

        assertEquals(3, result.getTotalRows());
        assertEquals(1, result.getInserted());
        assertEquals(1, result.getDuplicates());
        assertEquals(1, result.getInvalidRows());
        assertEquals(1, result.getCashbackProcessed());
    }

    private MockMultipartFile csv(String rows) {
        String header = "transactionRef,transactionDate,cardHash,maskedCard,cardBin,merchantId,merchantName,amount,currency,mccCode,channel,terminalId,status\n";
        return new MockMultipartFile("file", "transactions.csv", "text/csv",
                (header + rows).getBytes(StandardCharsets.UTF_8));
    }

    private CashbackDecision approved() {
        return CashbackDecision.approved(CashbackDecisionCode.APPROVED, "ok",
                new BigDecimal("25000"), new BigDecimal("1250"), null, 1L, 7L, null);
    }
}
