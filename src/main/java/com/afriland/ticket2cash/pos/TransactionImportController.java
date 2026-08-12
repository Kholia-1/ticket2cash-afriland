package com.afriland.ticket2cash.pos;

import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
public class TransactionImportController {

    private final TransactionImportService importService;
    private final PosTransactionRepository transactionRepository;
    private final CashbackPaymentRepository paymentRepository;
    private final CampaignRepository campaignRepository;

    public TransactionImportController(TransactionImportService importService,
                                       PosTransactionRepository transactionRepository,
                                       CashbackPaymentRepository paymentRepository,
                                       CampaignRepository campaignRepository) {
        this.importService = importService;
        this.transactionRepository = transactionRepository;
        this.paymentRepository = paymentRepository;
        this.campaignRepository = campaignRepository;
    }

    @org.springframework.web.bind.annotation.GetMapping
    public List<TransactionDto> listTransactions() {
        return transactionRepository.findTop100ByOrderByReceivedAtDesc().stream()
                .map(transaction -> TransactionDto.from(transaction,
                        paymentRepository.findByTransactionRef(transaction.getTransactionRef()).orElse(null),
                        campaignRepository))
                .collect(Collectors.toList());
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransactionImportService.ImportSummary> importCsv(
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(importService.importCsv(file));
    }

    public static class TransactionDto {
        private Long id;
        private String transactionRef;
        private String maskedCard;
        private BigDecimal amount;
        private String currency;
        private Long merchantId;
        private String merchantName;
        private String status;
        private String mccCode;
        private String channel;
        private String terminalId;
        private LocalDateTime transactionDate;
        private LocalDateTime receivedAt;
        private boolean matched;

        private BigDecimal cashbackAmount;
        private String cashbackStatus;
        private Long campaignId;
        private String campaignName;
        private String cashbackDecision;
        private String rejectionReason;
        private LocalDateTime cashbackProcessedAt;

        public static TransactionDto from(PosTransaction source, CashbackPayment payment,
                                          CampaignRepository campaignRepository) {
            TransactionDto dto = new TransactionDto();
            dto.id = source.getId();
            dto.transactionRef = source.getTransactionRef();
            dto.maskedCard = source.getMaskedCard();
            dto.amount = source.getAmount();
            dto.currency = source.getCurrency();
            dto.merchantId = source.getMerchantId();
            dto.merchantName = source.getMerchantName();
            dto.status = source.getStatus();
            dto.mccCode = source.getMccCode();
            dto.channel = source.getChannel();
            dto.terminalId = source.getTerminalId();
            dto.transactionDate = source.getTransactionDate();
            dto.receivedAt = source.getReceivedAt();
            dto.matched = source.isMatched();
            if (payment == null) {
                dto.cashbackStatus = "AUCUN_CASHBACK";
                dto.cashbackDecision = "NON_TRAITE";
            } else {
                dto.cashbackAmount = payment.getAmount();
                dto.cashbackStatus = payment.getStatus() == null ? "NON_TRAITE" : payment.getStatus().name();
                dto.campaignId = payment.getCampaignId();
                dto.cashbackDecision = payment.getStatus() == com.afriland.ticket2cash.cashback.CashbackPaymentStatus.FAILED
                        ? "REJECTED" : "APPROVED";
                dto.cashbackProcessedAt = payment.getProcessedAt();
                if (payment.getCampaignId() != null) {
                    dto.campaignName = campaignRepository.findById(payment.getCampaignId())
                            .map(campaign -> campaign.getName()).orElse(null);
                }
            }
            return dto;
        }
        public Long getId() { return id; }
        public String getTransactionRef() { return transactionRef; }
        public String getMaskedCard() { return maskedCard; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public Long getMerchantId() { return merchantId; }
        public String getMerchantName() { return merchantName; }
        public String getStatus() { return status; }
        public String getMccCode() { return mccCode; }
        public String getChannel() { return channel; }
        public String getTerminalId() { return terminalId; }
        public LocalDateTime getTransactionDate() { return transactionDate; }
        public LocalDateTime getReceivedAt() { return receivedAt; }
        public boolean isMatched() { return matched; }
        public BigDecimal getCashbackAmount() { return cashbackAmount; }
        public String getCashbackStatus() { return cashbackStatus; }
        public Long getCampaignId() { return campaignId; }
        public String getCampaignName() { return campaignName; }
        public String getCashbackDecision() { return cashbackDecision; }
        public String getRejectionReason() { return rejectionReason; }
        public LocalDateTime getCashbackProcessedAt() { return cashbackProcessedAt; }
    }
}
