package com.afriland.ticket2cash.pos;

import com.afriland.ticket2cash.apikey.ApiKey;
import com.afriland.ticket2cash.apikey.ApiKeyService;
import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Webhook endpoint for receiving POS transactions from the bank.
 * 
 * Flow:
 * 1. Customer pays at POS with Afriland prepaid card
 * 2. Bank POS system sends transaction data to POST /api/webhook/transaction
 * 3. Ticket2Cash stores it in pos_transactions table
 * 4. When customer scans their receipt on the app:
 *    - System compares card hash + merchant + amount
 *    - If match found → claim marked as VERIFIED
 *    - If no match → claim marked as UNVERIFIED (flagged for review)
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final PosTransactionRepository posRepository;
    private final MerchantRepository merchantRepository;
    private final AuditLogService auditLogService;
    private final ApiKeyService apiKeyService;

    public WebhookController(PosTransactionRepository posRepository,
                              MerchantRepository merchantRepository,
                              AuditLogService auditLogService,
                              ApiKeyService apiKeyService) {
        this.posRepository = posRepository;
        this.merchantRepository = merchantRepository;
        this.auditLogService = auditLogService;
        this.apiKeyService = apiKeyService;
    }

    /**
     * Receive a POS transaction from the bank system.
     * 
     * Expected JSON:
     * {
     *   "transactionRef": "TXN-2026070601234",
     *   "cardNumber": "4532XXXXXXXX1234",  (or "cardHash" if pre-hashed)
     *   "maskedCard": "****1234",
     *   "merchantName": "Afriland First bank Cameroun",
     *   "amount": 50000,
     *   "currency": "FCFA",
     *   "transactionDate": "2026-07-06T14:30:00",
     *   "items": [
     *     {"name": "CHICKEN", "price": 3100, "qty": 1},
     *     {"name": "STRAWBERRY", "price": 2600, "qty": 3}
     *   ]
     * }
     */
    @PostMapping("/transaction")
    public ResponseEntity<?> receiveTransaction(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        ApiKey apiKey = authenticateWebhook(request);
        if (apiKey == null) return unauthorized();
        return ingestTransaction(body, apiKey);
    }

    private ResponseEntity<?> ingestTransaction(Map<String, Object> body, ApiKey apiKey) {

        String transactionRef = (String) body.get("transactionRef");
        if (transactionRef == null || transactionRef.isEmpty()) {
            transactionRef = "POS-" + System.currentTimeMillis();
        }

        // Check for duplicate
        if (posRepository.findByTransactionRef(transactionRef).isPresent()) {
            return ResponseEntity.ok(Map.of(
                "status", "DUPLICATE",
                "message", "Transaction already received",
                "transactionRef", transactionRef
            ));
        }

        // Hash the card number if raw card provided
        String cardHash = (String) body.get("cardHash");
        if (cardHash == null || cardHash.isEmpty()) {
            String cardNumber = (String) body.get("cardNumber");
            if (cardNumber != null) {
                cardHash = hashCard(cardNumber.replaceAll("\\s", ""));
            }
        }

        String maskedCard = (String) body.get("maskedCard");
        String merchantName = (String) body.get("merchantName");
        Number amountNum = (Number) body.get("amount");
        BigDecimal amount = amountNum != null ? BigDecimal.valueOf(amountNum.doubleValue()) : BigDecimal.ZERO;
        if (amount.signum() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "amount must be greater than zero"));
        }

        // Try to match merchant
        Long merchantId = null;
        if (merchantName != null) {
            Merchant m = findMerchantByName(merchantName);
            if (m != null) merchantId = m.getId();
        }
        if (merchantId == null) {
            merchantId = apiKey.getMerchantId();
        }
        if (apiKey.getMerchantId() != null && !apiKey.getMerchantId().equals(merchantId)) {
            return ResponseEntity.status(403).body(Map.of("error", "API key is not authorized for this merchant"));
        }

        // Parse transaction date
        LocalDateTime txDate = LocalDateTime.now();
        if (body.get("transactionDate") != null) {
            try {
                txDate = LocalDateTime.parse(body.get("transactionDate").toString());
            } catch (Exception e) {
                // Keep current time
            }
        }

        // Store the transaction
        PosTransaction pos = new PosTransaction();
        pos.setTransactionRef(transactionRef);
        pos.setCardHash(cardHash);
        pos.setMaskedCard(maskedCard);
        pos.setMerchantName(merchantName);
        pos.setMerchantId(merchantId);
        pos.setAmount(amount);
        pos.setCurrency((String) body.getOrDefault("currency", "FCFA"));
        pos.setTransactionDate(txDate);
        try {
            pos = posRepository.save(pos);
        } catch (DataIntegrityViolationException duplicate) {
            return ResponseEntity.ok(Map.of(
                "status", "DUPLICATE",
                "message", "Transaction already received",
                "transactionRef", transactionRef
            ));
        }

        auditLogService.log("WEBHOOK_TRANSACTION", "POS", "PosTransaction",
            pos.getId(), "WEBHOOK", "SUCCESS",
            "POS transaction received: " + transactionRef
            + " card=" + maskedCard
            + " merchant=" + merchantName
            + " amount=" + amount);

        return ResponseEntity.ok(Map.of(
            "status", "RECEIVED",
            "transactionRef", transactionRef,
            "posTransactionId", pos.getId(),
            "merchantMatched", merchantId != null
        ));
    }

    /**
     * Receive batch transactions (multiple at once).
     */
    @PostMapping("/transactions/batch")
    @Transactional
    public ResponseEntity<?> receiveBatch(@RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        ApiKey apiKey = authenticateWebhook(request);
        if (apiKey == null) return unauthorized();

        Object rawTransactions = body.get("transactions");
        if (!(rawTransactions instanceof List<?>)) {
            return ResponseEntity.badRequest().body(Map.of("error", "transactions must be an array"));
        }
        List<?> rawList = (List<?>) rawTransactions;
        if (rawList.size() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Maximum batch size is 1000"));
        }

        List<Map<String, Object>> transactions = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Each transaction must be an object"));
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            transactions.add(normalized);
        }
        if (transactions == null || transactions.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No transactions provided"));
        }

        int received = 0, duplicates = 0;
        for (Map<String, Object> tx : transactions) {
            String ref = (String) tx.get("transactionRef");
            if (ref != null && posRepository.findByTransactionRef(ref).isPresent()) {
                duplicates++;
                continue;
            }
            ResponseEntity<?> response = ingestTransaction(tx, apiKey);
            if (response.getStatusCode().is2xxSuccessful()) received++;
        }

        return ResponseEntity.ok(Map.of(
            "status", "BATCH_PROCESSED",
            "received", received,
            "duplicates", duplicates,
            "total", transactions.size()
        ));
    }

    /**
     * Get all POS transactions (admin view).
     */
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(HttpServletRequest request) {
        if (!isAdmin(request)) return forbidden();
        List<PosTransaction> all = posRepository.findAllByOrderByReceivedAtDesc();

        List<Map<String, Object>> result = all.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("transactionRef", t.getTransactionRef());
            m.put("maskedCard", t.getMaskedCard());
            m.put("merchantName", t.getMerchantName());
            m.put("amount", t.getAmount());
            m.put("currency", t.getCurrency());
            m.put("transactionDate", t.getTransactionDate() != null ? t.getTransactionDate().toString() : "");
            m.put("receivedAt", t.getReceivedAt() != null ? t.getReceivedAt().toString() : "");
            m.put("matched", t.isMatched());
            m.put("matchedClaimId", t.getMatchedClaimId());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Get unmatched transactions (transactions without a corresponding scan).
     */
    @GetMapping("/transactions/unmatched")
    public ResponseEntity<?> getUnmatched(HttpServletRequest request) {
        if (!isAdmin(request)) return forbidden();
        return ResponseEntity.ok(posRepository.findByMatchedFalseOrderByReceivedAtDesc());
    }

    private ApiKey authenticateWebhook(HttpServletRequest request) {
        String rawKey = request.getHeader("X-API-Key");
        return apiKeyService.authenticate(rawKey).orElse(null);
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && "ADMIN".equals(String.valueOf(session.getAttribute("AUTH_ROLE")));
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "Valid X-API-Key required"));
    }

    private ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(403).body(Map.of("error", "ADMIN role required"));
    }

    private Merchant findMerchantByName(String name) {
        return merchantRepository.findAll().stream()
            .filter(m -> m.getName().equalsIgnoreCase(name)
                || (m.getBrandName() != null && m.getBrandName().equalsIgnoreCase(name)))
            .findFirst().orElse(null);
    }

    private String hashCard(String cardNumber) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(cardNumber.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return cardNumber;
        }
    }
}
