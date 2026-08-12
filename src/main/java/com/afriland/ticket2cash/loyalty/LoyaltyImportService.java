package com.afriland.ticket2cash.loyalty;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Parses uploaded bank-transaction files into {@link LoyaltyTransaction} rows.
 *
 * <p>Deliberately tolerant of Afriland's real-world exports: header names may be
 * in French or English, column order can vary, dates come in 5+ formats, and
 * amounts may include thousand separators and a trailing "CR"/"DR" indicator.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>CSV — comma, semicolon, or tab separated (auto-detected)</li>
 *   <li>XLSX — first sheet only</li>
 * </ul>
 *
 * <p>Recognized column headers (case-insensitive, accents stripped):
 * <table>
 *   <tr><td>accountNumber</td><td>compte, account, numero_compte, n_compte, account_no</td></tr>
 *   <tr><td>clientName</td><td>nom, client, name, fullname, nom_client</td></tr>
 *   <tr><td>transactionDate</td><td>date, date_operation, tx_date, operation_date</td></tr>
 *   <tr><td>amount</td><td>montant, amount, debit, credit, valeur</td></tr>
 *   <tr><td>description</td><td>libelle, description, motif, memo, narration</td></tr>
 *   <tr><td>category</td><td>categorie, category, type, type_operation</td></tr>
 *   <tr><td>referenceNumber</td><td>reference, ref, transaction_id, tx_ref, numero</td></tr>
 * </table>
 */
@Service
public class LoyaltyImportService {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
    };

    private static final Map<String, Set<String>> HEADER_ALIASES = new HashMap<>();
    static {
        HEADER_ALIASES.put("accountNumber", Set.of("compte", "account", "numero_compte", "n_compte", "account_no", "accountnumber", "no_compte", "acc"));
        HEADER_ALIASES.put("clientName",    Set.of("nom", "client", "name", "fullname", "nom_client", "clientname", "beneficiaire"));
        HEADER_ALIASES.put("transactionDate", Set.of("date", "date_operation", "tx_date", "operation_date", "transactiondate", "date_op"));
        HEADER_ALIASES.put("amount",        Set.of("montant", "amount", "debit", "credit", "valeur", "value", "mnt"));
        HEADER_ALIASES.put("description",   Set.of("libelle", "description", "motif", "memo", "narration", "designation"));
        HEADER_ALIASES.put("category",      Set.of("categorie", "category", "type", "type_operation", "categorie_op", "cat"));
        HEADER_ALIASES.put("referenceNumber", Set.of("reference", "ref", "transaction_id", "tx_ref", "numero", "reference_op", "id"));
        HEADER_ALIASES.put("entityType",    Set.of("entitytype", "type_entite", "type_client", "entrepriseouparticulier", "particulier_entreprise", "client_type", "typeclient"));
    }

    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyBatchRepository batchRepository;
    private final LoyaltyClientRepository clientRepository;

    public LoyaltyImportService(LoyaltyTransactionRepository transactionRepository,
                                LoyaltyBatchRepository batchRepository,
                                LoyaltyClientRepository clientRepository) {
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
        this.clientRepository = clientRepository;
    }

    /**
     * Parses a file and persists all rows against the given batch.
     * Returns the updated batch with row counters populated.
     */
    @Transactional
    public LoyaltyBatch importFile(LoyaltyBatch batch, String filename, byte[] bytes) {
        List<LoyaltyTransaction> rows;
        try {
            String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                rows = parseExcel(bytes);
            } else {
                rows = parseCsv(bytes);
            }
        } catch (Exception e) {
            batch.setStatus(LoyaltyBatchStatus.FAILED);
            batch.setNote("Import failed: " + e.getMessage());
            return batchRepository.save(batch);
        }

        int total = rows.size();
        int parsed = 0;
        int failed = 0;

        // Step 1: sync clients with entityType hints from the file, before persisting txs
        syncClientsFromRows(rows);

        for (LoyaltyTransaction row : rows) {
            if (row.getAccountNumber() == null || row.getAccountNumber().isBlank()
                    || row.getAmount() == null || row.getTransactionDate() == null) {
                failed++;
                continue;
            }
            row.setBatchId(batch.getId());
            transactionRepository.save(row);
            parsed++;
        }

        batch.setTotalRows(total);
        batch.setParsedRows(parsed);
        batch.setFailedRows(failed);
        batch.setStatus(LoyaltyBatchStatus.IMPORTED);
        batch.setNote(String.format("Parsed %d of %d rows (%d skipped for missing required fields).",
                parsed, total, failed));

        return batchRepository.save(batch);
    }

    // ---------- CSV ----------

    private List<LoyaltyTransaction> parseCsv(byte[] bytes) throws Exception {
        List<LoyaltyTransaction> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {

            String header = br.readLine();
            if (header == null) return out;

            char sep = detectSeparator(header);
            String[] rawHeaders = splitCsv(header, sep);
            Map<String, Integer> colIndex = mapHeaders(rawHeaders);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cells = splitCsv(line, sep);
                LoyaltyTransaction tx = rowToTransaction(cells, colIndex);
                if (tx != null) out.add(tx);
            }
        }
        return out;
    }

    private char detectSeparator(String header) {
        int c = countChar(header, ',');
        int sc = countChar(header, ';');
        int t = countChar(header, '\t');
        if (sc >= c && sc >= t) return ';';
        if (t > c) return '\t';
        return ',';
    }

    private int countChar(String s, char ch) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ch) n++;
        return n;
    }

    /** Minimal CSV split that handles double-quoted cells with the separator inside. */
    private String[] splitCsv(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == sep && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    // ---------- Excel ----------

    private List<LoyaltyTransaction> parseExcel(byte[] bytes) throws Exception {
        List<LoyaltyTransaction> out = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return out;
            Iterator<Row> iter = sheet.iterator();
            if (!iter.hasNext()) return out;

            Row header = iter.next();
            String[] rawHeaders = new String[header.getLastCellNum()];
            for (int i = 0; i < rawHeaders.length; i++) {
                Cell c = header.getCell(i);
                rawHeaders[i] = c == null ? "" : cellToString(c);
            }
            Map<String, Integer> colIndex = mapHeaders(rawHeaders);

            while (iter.hasNext()) {
                Row row = iter.next();
                String[] cells = new String[Math.max(rawHeaders.length, row.getLastCellNum())];
                for (int i = 0; i < cells.length; i++) {
                    Cell c = row.getCell(i);
                    cells[i] = c == null ? "" : cellToString(c);
                }
                LoyaltyTransaction tx = rowToTransaction(cells, colIndex);
                if (tx != null) out.add(tx);
            }
        }
        return out;
    }

    private String cellToString(Cell c) {
        try {
            switch (c.getCellType()) {
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(c)) {
                        LocalDate d = c.getDateCellValue().toInstant()
                                .atZone(ZoneId.systemDefault()).toLocalDate();
                        return d.toString();
                    }
                    // Format as plain number to preserve integer account numbers
                    double v = c.getNumericCellValue();
                    if (v == Math.floor(v) && !Double.isInfinite(v)) {
                        return Long.toString((long) v);
                    }
                    return Double.toString(v);
                case BOOLEAN: return Boolean.toString(c.getBooleanCellValue());
                case FORMULA: return c.getCellFormula();
                case BLANK: return "";
                default: return c.getStringCellValue().trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- Header + row mapping ----------

    private Map<String, Integer> mapHeaders(String[] rawHeaders) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < rawHeaders.length; i++) {
            String norm = normalize(rawHeaders[i]);
            for (Map.Entry<String, Set<String>> entry : HEADER_ALIASES.entrySet()) {
                if (entry.getValue().contains(norm) && !out.containsKey(entry.getKey())) {
                    out.put(entry.getKey(), i);
                }
            }
        }
        return out;
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "_").replace('-', '_');
    }

    private LoyaltyTransaction rowToTransaction(String[] cells, Map<String, Integer> colIndex) {
        LoyaltyTransaction tx = new LoyaltyTransaction();
        tx.setAccountNumber(strip(get(cells, colIndex, "accountNumber")));
        tx.setClientName(strip(get(cells, colIndex, "clientName")));
        tx.setDescription(strip(get(cells, colIndex, "description")));
        tx.setCategory(strip(get(cells, colIndex, "category")));
        tx.setReferenceNumber(strip(get(cells, colIndex, "referenceNumber")));

        String date = get(cells, colIndex, "transactionDate");
        tx.setTransactionDate(parseDate(date));

        String amt = get(cells, colIndex, "amount");
        tx.setAmount(parseAmount(amt));

        // Entity-type hint (INDIVIDUAL / COMPANY) — carried via @Transient field
        String et = normalizeEntityType(get(cells, colIndex, "entityType"));
        tx.setImportedEntityType(et);

        tx.setImportedAt(LocalDateTime.now());
        return tx;
    }

    /**
     * Maps free-form values to the two canonical entity types.
     * Accepts French ("entreprise", "particulier", "société"), English
     * ("company", "individual", "corporation", "personal"), and short codes
     * ("E"/"P", "C"/"I"). Returns null when unrecognized so downstream code
     * can fall back to defaults.
     */
    String normalizeEntityType(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return null;
        if (v.startsWith("e") || v.startsWith("c") || v.contains("entrep")
                || v.contains("company") || v.contains("corp") || v.contains("societ")
                || v.contains("business") || v.contains("sarl") || v.contains("sa"))
            return "COMPANY";
        if (v.startsWith("p") || v.startsWith("i") || v.contains("part")
                || v.contains("indiv") || v.contains("person"))
            return "INDIVIDUAL";
        return null;
    }

    /**
     * For every distinct account number in the parsed rows, upsert its
     * {@link LoyaltyClient}: create if missing, patch entityType/name if
     * previously blank/default. Never overrides a manually-set entityType.
     */
    private void syncClientsFromRows(List<LoyaltyTransaction> rows) {
        // Collapse rows down to one authoritative view per account
        java.util.Map<String, String[]> perAccount = new java.util.LinkedHashMap<>();
        // Value format: [entityType, clientName]
        for (LoyaltyTransaction r : rows) {
            String acc = r.getAccountNumber();
            if (acc == null || acc.isBlank()) continue;
            String[] cur = perAccount.computeIfAbsent(acc, k -> new String[]{null, null});
            if (cur[0] == null && r.getImportedEntityType() != null) cur[0] = r.getImportedEntityType();
            if (cur[1] == null && r.getClientName() != null) cur[1] = r.getClientName();
        }

        for (java.util.Map.Entry<String, String[]> e : perAccount.entrySet()) {
            String acc = e.getKey();
            String hintedType = e.getValue()[0];
            String hintedName = e.getValue()[1];

            LoyaltyClient c = clientRepository.findByAccountNumber(acc).orElse(null);
            if (c == null) {
                c = new LoyaltyClient();
                c.setAccountNumber(acc);
                c.setFullName(hintedName);
                c.setEntityType(hintedType != null ? hintedType : "INDIVIDUAL");
                clientRepository.save(c);
            } else {
                boolean touched = false;
                // Only overwrite entityType if the client was on the default
                // AND the file gave us a definite hint. Never silently downgrade.
                if (hintedType != null && ("INDIVIDUAL".equals(c.getEntityType()) || c.getEntityType() == null)) {
                    if (!hintedType.equals(c.getEntityType())) {
                        c.setEntityType(hintedType);
                        touched = true;
                    }
                }
                if ((c.getFullName() == null || c.getFullName().isBlank()) && hintedName != null) {
                    c.setFullName(hintedName);
                    touched = true;
                }
                if (touched) clientRepository.save(c);
            }
        }
    }

    private String get(String[] cells, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i < 0 || i >= cells.length) return null;
        return cells[i];
    }

    private String strip(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private LocalDate parseDate(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        // Some Excel exports give "yyyy-MM-dd 00:00:00" — trim to date part
        if (t.length() >= 10 && (t.charAt(4) == '-' || t.charAt(2) == '/')) {
            String prefix = t.substring(0, 10);
            for (DateTimeFormatter f : DATE_FORMATS) {
                try { return LocalDate.parse(prefix, f); } catch (Exception ignored) {}
            }
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(t, f); } catch (Exception ignored) {}
        }
        return null;
    }

    private BigDecimal parseAmount(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        // Detect sign markers used in some bank exports
        boolean negative = false;
        String upper = t.toUpperCase(Locale.ROOT);
        if (upper.endsWith("DR") || upper.endsWith("D") || upper.endsWith("-")) negative = true;
        if (upper.startsWith("-") || (upper.startsWith("(") && upper.endsWith(")"))) negative = true;

        // Strip currency codes, spaces, thousand separators
        String cleaned = t.replaceAll("(?i)[a-z]", "")
                          .replaceAll("[()\\s]", "")
                          .replace("'", "");

        // Handle European decimals: last "," or "." is decimal; the rest are separators
        int lastComma = cleaned.lastIndexOf(',');
        int lastDot   = cleaned.lastIndexOf('.');
        int decimalPos = Math.max(lastComma, lastDot);
        if (decimalPos > 0 && decimalPos == cleaned.length() - 3) {
            // Treat as decimal separator
            String intPart = cleaned.substring(0, decimalPos).replaceAll("[,.]", "");
            String fracPart = cleaned.substring(decimalPos + 1);
            cleaned = intPart + "." + fracPart;
        } else {
            cleaned = cleaned.replaceAll("[,.]", "");
        }
        cleaned = cleaned.replace("+", "").replace("-", "");
        if (cleaned.isEmpty()) return null;

        try {
            BigDecimal v = new BigDecimal(cleaned);
            return negative ? v.negate() : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
