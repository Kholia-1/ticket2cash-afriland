package com.afriland.ticket2cash.mobile;

import com.afriland.ticket2cash.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/mobile")
@CrossOrigin(origins = "*")
public class MobileAuthController {

    private final MobileClientRepository clientRepository;
    private final AuditLogService auditLogService;

    // OTP storage: phone -> {code, expiresAt}
    private static final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    public MobileAuthController(MobileClientRepository clientRepository,
                                 AuditLogService auditLogService) {
        this.clientRepository = clientRepository;
        this.auditLogService = auditLogService;
    }

    // ─── OTP ───

    /**
     * Send OTP to phone number.
     * In production: integrate with Orange/MTN SMS gateway.
     * For prototype: returns OTP in response (remove in production!)
     */
    @PostMapping("/otp/send")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PHONE",
                "message", "Phone number is required"
            ));
        }

        String cleanPhone = phone.replaceAll("[^0-9+]", "");

        // Generate 4-digit OTP
        String otp = String.format("%04d", new Random().nextInt(10000));

        // Store with 5 minute expiry
        otpStore.put(cleanPhone, new OtpEntry(otp, LocalDateTime.now().plusMinutes(5)));

        System.out.println("[OTP] Code " + otp + " sent to " + cleanPhone);

        // In production: call SMS API here
        // smsService.send(cleanPhone, "Votre code Ticket2Cash: " + otp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", true);
        result.put("phone", cleanPhone);
        result.put("expiresInSeconds", 300);
        // PROTOTYPE ONLY - remove in production!
        result.put("otp_debug", otp);

        return ResponseEntity.ok(result);
    }

    /**
     * Verify OTP code.
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");

        if (phone == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_FIELDS",
                "message", "phone and code are required"
            ));
        }

        String cleanPhone = phone.replaceAll("[^0-9+]", "");
        OtpEntry entry = otpStore.get(cleanPhone);

        if (entry == null) {
            return ResponseEntity.status(400).body(Map.of(
                "verified", false,
                "error", "OTP_NOT_FOUND",
                "message", "No OTP sent to this number. Request a new one."
            ));
        }

        if (LocalDateTime.now().isAfter(entry.expiresAt)) {
            otpStore.remove(cleanPhone);
            return ResponseEntity.status(400).body(Map.of(
                "verified", false,
                "error", "OTP_EXPIRED",
                "message", "OTP has expired. Request a new one."
            ));
        }

        if (!entry.code.equals(code.trim())) {
            return ResponseEntity.status(400).body(Map.of(
                "verified", false,
                "error", "OTP_INVALID",
                "message", "Invalid OTP code"
            ));
        }

        // OTP valid - mark phone as verified
        otpStore.remove(cleanPhone);

        return ResponseEntity.ok(Map.of(
            "verified", true,
            "phone", cleanPhone
        ));
    }

    // ─── Registration ───

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body,
                                       HttpServletRequest request) {
        String phone = body.get("phone");
        String cardNumber = body.get("cardNumber");
        String pin = body.get("pin");
        String fullName = body.get("fullName");

        if (phone == null || cardNumber == null || pin == null || fullName == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_FIELDS",
                "message", "phone, cardNumber, pin, and fullName are required"
            ));
        }

        String cleanPhone = phone.replaceAll("[^0-9+]", "");

        // Check phone uniqueness
        if (clientRepository.existsByPhone(cleanPhone)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PHONE_EXISTS",
                "message", "Un compte avec ce numero existe deja"
            ));
        }

        // Check card uniqueness
        String cardHash = sha256(cardNumber.replaceAll("\\s", ""));
        if (clientRepository.existsByCardHash(cardHash)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "CARD_EXISTS",
                "message", "Cette carte est deja liee a un autre compte"
            ));
        }

        MobileClient client = new MobileClient();
        client.setPhone(cleanPhone);
        client.setFullName(fullName);
        client.setPinHash(sha256(pin));
        client.setCardHash(cardHash);

        String last4 = cardNumber.length() >= 4
            ? cardNumber.substring(cardNumber.length() - 4)
            : cardNumber;
        client.setMaskedCard("**** **** **** " + last4);

        client = clientRepository.save(client);

        HttpSession session = request.getSession(true);
        session.setAttribute("MOBILE_CLIENT_ID", client.getId());
        session.setAttribute("MOBILE_CLIENT_PHONE", client.getPhone());

        auditLogService.log("MOBILE_REGISTER", "mobile", "MobileClient",
            client.getId(), cleanPhone, "SUCCESS",
            "New mobile client registered: " + fullName + " card=" + client.getMaskedCard());

        return ResponseEntity.ok(clientToMap(client));
    }

    // ─── Login ───

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                    HttpServletRequest request) {
        String phone = body.get("phone");
        String pin = body.get("pin");

        if (phone == null || pin == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_FIELDS",
                "message", "phone and pin are required"
            ));
        }

        String cleanPhone = phone.replaceAll("[^0-9+]", "");

        var clientOpt = clientRepository.findByPhone(cleanPhone);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "INVALID_CREDENTIALS",
                "message", "Numero ou PIN incorrect"
            ));
        }

        MobileClient client = clientOpt.get();

        if (!sha256(pin).equals(client.getPinHash())) {
            auditLogService.log("MOBILE_LOGIN_FAILED", "mobile", "MobileClient",
                client.getId(), cleanPhone, "FAILED", "Wrong PIN");
            return ResponseEntity.status(401).body(Map.of(
                "error", "INVALID_CREDENTIALS",
                "message", "Numero ou PIN incorrect"
            ));
        }

        if (!Boolean.TRUE.equals(client.getActive())) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "ACCOUNT_DISABLED",
                "message", "Compte desactive"
            ));
        }

        client.setLastLoginAt(LocalDateTime.now());
        clientRepository.save(client);

        HttpSession session = request.getSession(true);
        session.setAttribute("MOBILE_CLIENT_ID", client.getId());
        session.setAttribute("MOBILE_CLIENT_PHONE", client.getPhone());

        auditLogService.log("MOBILE_LOGIN", "mobile", "MobileClient",
            client.getId(), cleanPhone, "SUCCESS", "Mobile client logged in");

        return ResponseEntity.ok(clientToMap(client));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Long clientId = getMobileClientId(request);
        if (clientId == null) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "NOT_AUTHENTICATED",
                "message", "Please login first"
            ));
        }
        var clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        }
        return ResponseEntity.ok(clientToMap(clientOpt.get()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    static Long getMobileClientId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute("MOBILE_CLIENT_ID");
        if (id instanceof Long) return (Long) id;
        return null;
    }

    private Map<String, Object> clientToMap(MobileClient c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("phone", c.getPhone());
        map.put("fullName", c.getFullName());
        map.put("maskedCard", c.getMaskedCard());
        map.put("tier", c.getTier());
        map.put("tierPoints", c.getTierPoints());
        map.put("active", c.getActive());
        map.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        return map;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static class OtpEntry {
        final String code;
        final LocalDateTime expiresAt;
        OtpEntry(String code, LocalDateTime expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }
}
