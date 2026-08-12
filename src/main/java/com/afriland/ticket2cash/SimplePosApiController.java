package com.afriland.ticket2cash;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/v1")
public class SimplePosApiController {

    private static final List<Map<String, Object>> POS_TICKETS = new CopyOnWriteArrayList<>();

    @GetMapping("/docs")
    public Map<String, Object> docs() {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(endpoint("POST", "/api/v1/card/verify", "Verifier une carte prepayee Afriland."));
        endpoints.add(endpoint("POST", "/api/v1/verify-card", "Alias verification carte."));
        endpoints.add(endpoint("POST", "/api/v1/ticket/upload", "Envoyer un ticket depuis un POS supermarche."));
        endpoints.add(endpoint("POST", "/api/v1/ticket/submit", "Alias soumission ticket POS."));
        endpoints.add(endpoint("POST", "/api/v1/create-transaction", "Alias creation transaction POS."));
        endpoints.add(endpoint("GET", "/api/v1/cashback/status/{ticketNumber}", "Verifier le statut cashback d'un ticket."));
        endpoints.add(endpoint("GET", "/api/v1/transaction/history/{clientReference}", "Historique POS d'un client."));
        endpoints.add(endpoint("GET", "/api/v1/merchant/products/{merchantId}", "Catalogue produits demo d'un commercant."));
        endpoints.add(endpoint("POST", "/api/v1/validate-ticket", "Valider rapidement un ticket POS."));
        endpoints.add(endpoint("POST", "/api/v1/apply-discount", "Calculer un cashback estime."));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiName", "Ticket2Cash POS Integration API");
        result.put("version", "v1-demo");
        result.put("securityDemo", "Prototype. Production : OAuth2/JWT, mTLS, API Keys HMAC.");
        result.put("generatedAt", LocalDateTime.now().toString());
        result.put("endpoints", endpoints);

        return result;
    }

    @PostMapping({"/card/verify", "/verify-card"})
    public Map<String, Object> verifyCard(@RequestBody Map<String, Object> payload) {
        String cardNumber = text(payload.get("cardNumber"));
        String clientReference = defaultText(text(payload.get("clientReference")), "CLIENT-DEMO-001");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eligible", true);
        result.put("cardStatus", "ACTIVE");
        result.put("clientReference", clientReference);
        result.put("cardMasked", maskCard(cardNumber));
        result.put("cardProgram", "AFB_PREPAID_COBRAND");
        result.put("message", "Carte eligible au programme Ticket2Cash.");
        result.put("verifiedAt", LocalDateTime.now().toString());

        return result;
    }

    @PostMapping({"/ticket/upload", "/ticket/submit", "/create-transaction"})
    public Map<String, Object> uploadTicket(@RequestBody Map<String, Object> payload) {
        String ticketNumber = defaultText(text(payload.get("ticketNumber")), "POS-" + System.currentTimeMillis());
        String clientReference = defaultText(text(payload.get("clientReference")), "CLIENT-DEMO-001");
        String merchantName = defaultText(text(payload.get("merchantName")), "Supermarche Demo");

        BigDecimal totalAmount = money(payload.get("totalAmount"));
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            totalAmount = money(payload.get("amount"));
        }

        BigDecimal cashback = money(payload.get("cashbackEstimated"));
        if (cashback.compareTo(BigDecimal.ZERO) == 0) {
            cashback = totalAmount.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        }

        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("accepted", true);
        ticket.put("ticketNumber", ticketNumber);
        ticket.put("status", "SUBMITTED");
        ticket.put("clientReference", clientReference);
        ticket.put("cardMasked", maskCard(text(payload.get("cardNumber"))));
        ticket.put("merchantId", payload.get("merchantId"));
        ticket.put("merchantName", merchantName);
        ticket.put("posId", defaultText(text(payload.get("posId")), "POS-001"));
        ticket.put("cashierId", defaultText(text(payload.get("cashierId")), "CAISSE-001"));
        ticket.put("totalAmount", totalAmount);
        ticket.put("cashbackEstimated", cashback);
        ticket.put("currency", defaultText(text(payload.get("currency")), "FCFA"));
        ticket.put("expectedCreditDate", LocalDate.now().plusDays(1).toString());
        ticket.put("message", "Ticket POS recu. En attente du batch cashback J+1.");
        ticket.put("createdAt", LocalDateTime.now().toString());

        POS_TICKETS.add(0, ticket);

        return ticket;
    }

    @GetMapping("/cashback/status/{ticketNumber}")
    public Map<String, Object> cashbackStatus(@PathVariable String ticketNumber) {
        for (Map<String, Object> ticket : POS_TICKETS) {
            if (ticketNumber.equals(String.valueOf(ticket.get("ticketNumber")))) {
                Map<String, Object> result = new LinkedHashMap<>(ticket);
                result.put("found", true);
                return result;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", false);
        result.put("ticketNumber", ticketNumber);
        result.put("status", "UNKNOWN");
        result.put("message", "Ticket introuvable.");
        return result;
    }

    @PostMapping("/validate-ticket")
    public Map<String, Object> validateTicket(@RequestBody Map<String, Object> payload) {
        return cashbackStatus(text(payload.get("ticketNumber")));
    }

    @GetMapping("/transaction/history/{clientReference}")
    public List<Map<String, Object>> history(@PathVariable String clientReference) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> ticket : POS_TICKETS) {
            if (clientReference.equals(String.valueOf(ticket.get("clientReference")))) {
                result.add(new LinkedHashMap<>(ticket));
            }
        }

        return result;
    }

    @GetMapping({"/merchant/products/{merchantId}", "/merchant-products/{merchantId}"})
    public List<Map<String, Object>> merchantProducts(@PathVariable String merchantId) {
        List<Map<String, Object>> products = new ArrayList<>();

        products.add(product(merchantId, "SKU-DEMO-001", "Riz parfume 5KG", "Alimentation", "12500", "PERCENTAGE", "5"));
        products.add(product(merchantId, "SKU-DEMO-002", "Huile vegetale 5L", "Alimentation", "16000", "PERCENTAGE", "5"));
        products.add(product(merchantId, "SKU-DEMO-003", "Lait en poudre 400G", "Epicerie", "4500", "FIXED_AMOUNT", "250"));

        return products;
    }

    @PostMapping("/apply-discount")
    public Map<String, Object> applyDiscount(@RequestBody Map<String, Object> payload) {
        BigDecimal amount = money(payload.get("amount"));
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            amount = money(payload.get("totalAmount"));
        }

        BigDecimal rate = money(payload.get("rate"));
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            rate = new BigDecimal("5");
        }

        BigDecimal cashback = amount.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("amount", amount);
        result.put("ratePercent", rate);
        result.put("cashbackEstimated", cashback);
        result.put("currency", defaultText(text(payload.get("currency")), "FCFA"));
        result.put("message", "Estimation cashback calculee.");

        return result;
    }

    private static Map<String, Object> endpoint(String method, String path, String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", method);
        result.put("path", path);
        result.put("description", description);
        return result;
    }

    private static Map<String, Object> product(String merchantId, String sku, String name, String category, String price, String cashbackType, String cashbackValue) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantId", merchantId);
        result.put("sku", sku);
        result.put("name", name);
        result.put("ticketDesignation", name);
        result.put("category", category);
        result.put("brand", "DEMO");
        result.put("price", price);
        result.put("cashbackType", cashbackType);
        result.put("cashbackValue", cashbackValue);
        result.put("active", true);
        return result;
    }

    private static String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static String maskCard(String cardNumber) {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("[^0-9]", "");

        if (digits.length() < 4) {
            return "**** **** **** 0000";
        }

        String last4 = digits.substring(digits.length() - 4);
        return "**** **** **** " + last4;
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }

        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }

        try {
            return new BigDecimal(String.valueOf(value).replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}