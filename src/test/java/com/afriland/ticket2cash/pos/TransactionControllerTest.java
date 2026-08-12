package com.afriland.ticket2cash.pos;

import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock PosTransactionRepository repository;
    @Mock TransactionImportService importService;
    @Mock CashbackPaymentRepository paymentRepository;
    @Mock CampaignRepository campaignRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new TransactionImportController(importService, repository, paymentRepository, campaignRepository)).build();
    }

    @Test
    void getTransactionsReturnsExpectedDtoFields() throws Exception {
        PosTransaction transaction = new PosTransaction();
        transaction.setId(12L);
        transaction.setTransactionRef("TX-001");
        transaction.setMaskedCard("****1234");
        transaction.setAmount(new BigDecimal("25000"));
        transaction.setCurrency("FCFA");
        transaction.setMerchantName("Shop Afriland");
        transaction.setStatus("SUCCESS");
        transaction.setReceivedAt(LocalDateTime.of(2026, 8, 12, 10, 30));
        when(repository.findTop100ByOrderByReceivedAtDesc()).thenReturn(List.of(transaction));
        when(paymentRepository.findByTransactionRef("TX-001")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionRef").value("TX-001"))
                .andExpect(jsonPath("$[0].maskedCard").value("****1234"))
                .andExpect(jsonPath("$[0].amount").value(25000))
                .andExpect(jsonPath("$[0].merchantName").value("Shop Afriland"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].cashbackStatus").value("AUCUN_CASHBACK"))
                .andExpect(jsonPath("$[0].cashbackDecision").value("NON_TRAITE"))
                .andExpect(jsonPath("$[0].receivedAt").exists());
    }

    @Test
    void getTransactionsReturnsCashbackFieldsWhenPaymentExists() throws Exception {
        PosTransaction transaction = new PosTransaction();
        transaction.setTransactionRef("TX-002");
        transaction.setAmount(new BigDecimal("10000"));
        CashbackPayment payment = new CashbackPayment();
        payment.setTransactionRef("TX-002");
        payment.setAmount(new BigDecimal("500"));
        payment.setCampaignId(9L);
        payment.setStatus(com.afriland.ticket2cash.cashback.CashbackPaymentStatus.PENDING);
        when(repository.findTop100ByOrderByReceivedAtDesc()).thenReturn(List.of(transaction));
        when(paymentRepository.findByTransactionRef("TX-002")).thenReturn(Optional.of(payment));
        when(campaignRepository.findById(9L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cashbackAmount").value(500))
                .andExpect(jsonPath("$[0].cashbackStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].campaignId").value(9))
                .andExpect(jsonPath("$[0].cashbackDecision").value("APPROVED"));
    }
}
