package com.afriland.ticket2cash.pos;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.cashback.engine.CashbackDecision;
import com.afriland.ticket2cash.cashback.engine.CashbackTransactionRequest;
import com.afriland.ticket2cash.cashback.engine.TransactionCashbackProcessingService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TransactionImportService {

    private static final String DEFAULT_CURRENCY = "FCFA";
    private static final String DEFAULT_STATUS = "SUCCESS";

    private final PosTransactionRepository posRepository;
    private final TransactionCashbackProcessingService cashbackProcessingService;
    private final AuditLogService auditLogService;

    public TransactionImportService(PosTransactionRepository posRepository,
                                    TransactionCashbackProcessingService cashbackProcessingService,
                                    AuditLogService auditLogService) {
        this.posRepository = posRepository;
        this.cashbackProcessingService = cashbackProcessingService;
        this.auditLogService = auditLogService;
    }

    public ImportSummary importCsv(MultipartFile file) throws IOException {
        ImportSummary summary = new ImportSummary();
        if (file == null || file.isEmpty()) {
            summary.error("Fichier CSV vide ou absent");
            return summary;
        }

        audit("TRANSACTION_CSV_IMPORT_STARTED", null, "CSV transaction import started");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                summary.error("En-tête CSV manquant");
                return summary;
            }
            List<String> headers = parseLine(stripBom(headerLine));
            Map<String, Integer> columns = indexColumns(headers);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                summary.totalRows++;
                try {
                    Map<String, String> row = rowValues(columns, parseLine(line));
                    importRow(row, lineNumber, summary);
                } catch (Exception ex) {
                    summary.invalidRows++;
                    summary.error("Ligne " + lineNumber + ": " + safeMessage(ex));
                    audit("TRANSACTION_CSV_ROW_INVALID", null,
                            "Invalid transaction row " + lineNumber + ": " + safeMessage(ex));
                }
            }
        }
        audit("IMPORT_CARD_TRANSACTIONS_CSV", null,
                "Lignes: " + summary.totalRows + " | Insérées: " + summary.inserted
                        + " | Doublons: " + summary.duplicates + " | Invalides: " + summary.invalidRows
                        + " | Cashback approuvés: " + summary.cashbackApproved + " | Cashback rejetés: " + summary.cashbackRejected);
        audit("TRANSACTION_CSV_IMPORT_COMPLETED", null,
                "CSV import completed: total=" + summary.totalRows
                        + ", inserted=" + summary.inserted
                        + ", duplicates=" + summary.duplicates
                        + ", invalid=" + summary.invalidRows);
        return summary;
    }

    private void importRow(Map<String, String> row, int lineNumber, ImportSummary summary) {
        String ref = value(row, "transactionRef");
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException("transactionRef obligatoire");
        ref = ref.trim();
        if (posRepository.existsByTransactionRef(ref)) {
            summary.duplicates++;
            audit("TRANSACTION_CSV_DUPLICATE", null, "Duplicate transactionRef " + ref);
            return;
        }

        String cardHash = value(row, "cardHash");
        if (cardHash == null || cardHash.isBlank()) throw new IllegalArgumentException("cardHash obligatoire");
        String amountText = value(row, "amount");
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText == null ? "" : amountText.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("amount invalide");
        }
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount doit être supérieur à zéro");

        LocalDateTime transactionDate = parseDate(value(row, "transactionDate"));
        Long merchantId = parseLong(value(row, "merchantId"));

        PosTransaction transaction = new PosTransaction();
        transaction.setTransactionRef(ref);
        transaction.setTransactionDate(transactionDate);
        transaction.setCardHash(cardHash.trim());
        transaction.setMaskedCard(value(row, "maskedCard"));
        transaction.setCardBin(value(row, "cardBin"));
        transaction.setMerchantId(merchantId);
        transaction.setMerchantName(value(row, "merchantName"));
        transaction.setAmount(amount);
        transaction.setCurrency(defaultValue(value(row, "currency"), DEFAULT_CURRENCY));
        transaction.setMccCode(value(row, "mccCode"));
        transaction.setChannel(value(row, "channel"));
        transaction.setTerminalId(value(row, "terminalId"));
        transaction.setStatus(defaultValue(value(row, "status"), DEFAULT_STATUS));
        transaction.setSource(defaultValue(value(row, "source"), "CSV_IMPORT"));

        try {
            transaction = posRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException duplicate) {
            summary.duplicates++;
            audit("TRANSACTION_CSV_DUPLICATE", null, "Duplicate transactionRef " + ref);
            return;
        }

        summary.inserted++;
        audit("TRANSACTION_CSV_INSERTED", transaction.getId(),
                "Transaction imported: " + ref + " amount=" + amount);

        CashbackDecision decision = cashbackProcessingService.process(toCashbackRequest(transaction));
        summary.cashbackProcessed++;
        if (decision != null && decision.isEligible()) summary.cashbackApproved++;
        else summary.cashbackRejected++;
    }

    private CashbackTransactionRequest toCashbackRequest(PosTransaction transaction) {
        CashbackTransactionRequest request = new CashbackTransactionRequest();
        request.setTransactionRef(transaction.getTransactionRef());
        request.setCardHash(transaction.getCardHash());
        request.setMaskedCard(transaction.getMaskedCard());
        request.setCardBin(transaction.getCardBin());
        request.setMerchantId(transaction.getMerchantId());
        request.setMerchantName(transaction.getMerchantName());
        request.setAmount(transaction.getAmount());
        request.setCurrency(transaction.getCurrency());
        request.setTransactionDateTime(transaction.getTransactionDate());
        request.setMccCode(transaction.getMccCode());
        request.setChannel(transaction.getChannel());
        request.setSource(transaction.getSource());
        return request;
    }

    private Map<String, Integer> indexColumns(List<String> headers) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            columns.put(headers.get(i).trim(), i);
        }
        return columns;
    }

    private Map<String, String> rowValues(Map<String, Integer> columns, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        columns.forEach((name, index) -> row.put(name, index < values.size() ? values.get(index).trim() : ""));
        return row;
    }

    private List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else current.append(c);
        }
        values.add(current.toString());
        return values;
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("transactionDate doit être ISO LocalDateTime");
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.valueOf(value.trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("merchantId invalide"); }
    }

    private String value(Map<String, String> row, String key) { return row.get(key); }
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String stripBom(String value) { return value != null && !value.isEmpty() && value.charAt(0) == '\ufeff' ? value.substring(1) : value; }
    private String safeMessage(Exception ex) { return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }

    private void audit(String action, Long entityId, String message) {
        try { auditLogService.log(action, "POS", "PosTransaction", entityId, "CSV_IMPORT", "SUCCESS", message); }
        catch (RuntimeException ignored) { /* audit failure must not discard a valid import */ }
    }

    public static class ImportSummary {
        private int totalRows;
        private int inserted;
        private int duplicates;
        private int invalidRows;
        private int cashbackProcessed;
        private int cashbackApproved;
        private int cashbackRejected;
        private final List<String> errors = new ArrayList<>();

        private void error(String message) { errors.add(message); }
        public int getTotalRows() { return totalRows; }
        public int getInserted() { return inserted; }
        public int getDuplicates() { return duplicates; }
        public int getInvalidRows() { return invalidRows; }
        public int getCashbackProcessed() { return cashbackProcessed; }
        public int getCashbackApproved() { return cashbackApproved; }
        public int getCashbackRejected() { return cashbackRejected; }
        public List<String> getErrors() { return errors; }
    }
}
