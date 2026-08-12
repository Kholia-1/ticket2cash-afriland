package com.afriland.ticket2cash.pos;

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

    public TransactionImportController(TransactionImportService importService,
                                       PosTransactionRepository transactionRepository) {
        this.importService = importService;
        this.transactionRepository = transactionRepository;
    }

    @org.springframework.web.bind.annotation.GetMapping
    public List<TransactionDto> listTransactions() {
        return transactionRepository.findTop100ByOrderByReceivedAtDesc().stream()
                .map(TransactionDto::from)
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

        public static TransactionDto from(PosTransaction source) {
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
    }
}
