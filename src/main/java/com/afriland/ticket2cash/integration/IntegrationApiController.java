package com.afriland.ticket2cash.integration;

import com.afriland.ticket2cash.apikey.ApiKey;
import com.afriland.ticket2cash.apikey.ApiKeyService;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.pos.PosTransaction;
import com.afriland.ticket2cash.pos.PosTransactionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PUBLIC INTEGRATION API for supermarkets / partner systems.
 *
 * Authenticated with a header:   X-API-Key: t2c_xxxxxxxx...
 * (issued by the admin in the "API" page)
 *
 *   GET  /api/v1/ping                 -> check the key works
 *   POST /api/v1/transactions         -> push ONE live purchase transaction
 *   POST /api/v1/transactions/batch   -> push MANY at once
 *
 * This is the second API: the supermarket's till/system calls it in real time
 * so Ticket2Cash can reconcile receipts against real purchases (Phase 3).
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class IntegrationApiController {

    private final ApiKeyService apiKeyService;
    private final PosTransactionRepository posRepository;
    private final MerchantRepository merchantRepository;

    public IntegrationApiController(ApiKeyService apiKeyService,
                                    PosTransactionRepository posRepository,
                                    MerchantRepository merchantRepository) {
        this.apiKeyService = apiKeyService;
        this.posRepository = posRepository;
        this.merchantRepository = merchantRepository;
    }

    private ApiKey auth(HttpServletRequest request) {
        String key = request.getHeader("X-API-Key");
        if (key == null) key = request.getHeader("x-api-key");
        return apiKeyService.authenticate(key).orElse(null);
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping(HttpServletRequest request) {
        ApiKey k = auth(request);
        if (k == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid or missing X-API-Key"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("client", k.getName());
        out.put("merchantId", k.getMerchantId());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> pushOne(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        ApiKey k = auth(request);
        if (k == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid or missing X-API-Key"));
        Map<String, Object> result = ingest(body, k);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/transactions/batch")
    public ResponseEntity<?> pushBatch(@RequestBody List<Map<String, Object>> items, HttpServletRequest request) {
        ApiKey k = auth(request);
        if (k == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid or missing X-API-Key"));
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> item : items) results.add(ingest(item, k));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("received", items.size());
        out.put("results", results);
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> ingest(Map<String, Object> body, ApiKey key) {
        Map<String, Object> out = new LinkedHashMap<>();
        String ref = str(body.get("transactionRef"));
        out.put("transactionRef", ref);

        if (ref == null) { out.put("status", "REJECTED"); out.put("reason", "transactionRef required"); return out; }
        if (posRepository.findByTransactionRef(ref).isPresent()) {
            out.put("status", "DUPLICATE"); return out;
        }

        BigDecimal amount = dec(body.get("amount"));
        if (amount == null || amount.signum() <= 0) {
            out.put("status", "REJECTED"); out.put("reason", "amount invalid"); return out;
        }

        String merchantName = str(body.get("merchantName"));
        Long merchantId = key.getMerchantId();          // key is bound to a partner
        if (merchantId == null && merchantName != null) {
            Merchant m = findMerchantByName(merchantName);
            if (m != null) merchantId = m.getId();
        }

        PosTransaction tx = new PosTransaction();
        tx.setTransactionRef(ref);
        String cardNumber = str(body.get("cardNumber"));
        tx.setCardHash(cardNumber != null ? sha256(cardNumber.replaceAll("\\s", "")) : str(body.get("cardHash")));
        tx.setMaskedCard(str(body.get("maskedCard")));
        tx.setMerchantName(merchantName);
        tx.setMerchantId(merchantId);
        tx.setAmount(amount);
        tx.setCurrency(body.get("currency") == null ? "FCFA" : str(body.get("currency")));
        tx.setTransactionDate(parseDate(body.get("transactionDate")));
        tx.setReceivedAt(LocalDateTime.now());
        tx.setMatched(false);
        posRepository.save(tx);

        out.put("status", "RECEIVED");
        out.put("merchantMatched", merchantId != null);
        return out;
    }

    private Merchant findMerchantByName(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase();
        for (Merchant m : merchantRepository.findAll()) {
            String mn = m.getName() == null ? "" : m.getName().toLowerCase();
            String bn = m.getBrandName() == null ? "" : m.getBrandName().toLowerCase();
            if (mn.equals(n) || bn.equals(n) || (!mn.isBlank() && n.contains(mn)) || (!bn.isBlank() && n.contains(bn))) {
                return m;
            }
        }
        return null;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal dec(Object o) {
        if (o == null) return null;
        try { return new BigDecimal(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }

    private static LocalDateTime parseDate(Object o) {
        if (o == null) return LocalDateTime.now();
        String s = String.valueOf(o).trim();
        try { return LocalDateTime.parse(s); } catch (Exception ignored) { }
        try { return LocalDateTime.parse(s.replace(" ", "T")); } catch (Exception ignored) { }
        return LocalDateTime.now();
    }

    private static String sha256(String in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(in.hashCode());
        }
    }
}
