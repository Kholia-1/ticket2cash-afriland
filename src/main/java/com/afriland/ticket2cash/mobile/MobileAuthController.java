package com.afriland.ticket2cash.mobile;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.common.ValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@RestController
@RequestMapping("/api/mobile")
public class MobileAuthController {

    private final MobileClientRepository clientRepository;
    private final AuditLogService auditLogService;
    private final OtpVerificationRepository otpRepository;

    // OTP storage: phone -> {code, expiresAt}
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int OTP_RESEND_DELAY_SECONDS = 30;

    public MobileAuthController(MobileClientRepository clientRepository,
                                 AuditLogService auditLogService,
                                 OtpVerificationRepository otpRepository) {
        this.clientRepository = clientRepository;
        this.auditLogService = auditLogService;
        this.otpRepository = otpRepository;
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
        if (cleanPhone.isBlank() || ValidationUtils.validatePhone(cleanPhone) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_PHONE"));
        }

        OtpVerification current = otpRepository.findById(cleanPhone).orElse(null);
        if (current != null && LocalDateTime.now().isBefore(current.getSentAt().plusSeconds(OTP_RESEND_DELAY_SECONDS))) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "OTP_RATE_LIMITED",
                "message", "Please wait before requesting another code"
            ));
        }

        // Generate 4-digit OTP
        String otp = String.format("%04d", SECURE_RANDOM.nextInt(10000));

        // Store with 5 minute expiry
        OtpVerification verification = new OtpVerification();
        verification.setPhone(cleanPhone);
        verification.setCodeHash(sha256(otp));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verification.setSentAt(LocalDateTime.now());
        verification.setAttempts(0);
        verification.setVerifiedUntil(null);
        otpRepository.save(verification);

        // In production: call SMS API here
        // smsService.send(cleanPhone, "Votre code Ticket2Cash: " + otp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", true);
        result.put("phone", cleanPhone);
        result.put("expiresInSeconds", 300);
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
        if (ValidationUtils.validatePhone(cleanPhone) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_PHONE"));
        }
        OtpVerification entry = otpRepository.findById(cleanPhone).orElse(null);

        if (entry == null) {
            return ResponseEntity.status(400).body(Map.of(
                "verified", false,
                "error", "OTP_NOT_FOUND",
                "message", "No OTP sent to this number. Request a new one."
            ));
        }

        if (LocalDateTime.now().isAfter(entry.getExpiresAt())) {
            otpRepository.delete(entry);
            return ResponseEntity.status(400).body(Map.of(
                "verified", false,
                "error", "OTP_EXPIRED",
                "message", "OTP has expired. Request a new one."
            ));
        }

        if (!MessageDigest.isEqual(entry.getCodeHash().getBytes(StandardCharsets.UTF_8), sha256(code.trim()).getBytes(StandardCharsets.UTF_8))) {
            entry.setAttempts(entry.getAttempts() + 1);
            if (entry.getAttempts() >= OTP_MAX_ATTEMPTS) otpRepository.delete(entry);
            else otpRepository.save(entry);
            return ResponseEntity.status(400).body(Map.of(
                "verified", false,
                "error", "OTP_INVALID",
                "message", "Invalid OTP code"
            ));
        }

        // OTP valid - mark phone as verified
        entry.setVerifiedUntil(LocalDateTime.now().plusMinutes(10));
        otpRepository.save(entry);

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
        String cardDigits = cardNumber.replaceAll("[\\s-]", "");
        String nameError = ValidationUtils.validateName(fullName, "nom complet");
        if (nameError != null || ValidationUtils.validatePhone(cleanPhone) != null
                || ValidationUtils.validateCardNumber(cardDigits) != null
                || pin.length() < 4 || pin.length() > 12 || !pin.matches("\\d+")) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_PHONE_OR_PIN"));
        }

        OtpVerification verification = otpRepository.findById(cleanPhone).orElse(null);
        LocalDateTime verifiedUntil = verification == null ? null : verification.getVerifiedUntil();
        if (verifiedUntil == null || LocalDateTime.now().isAfter(verifiedUntil)) {
            if (verification != null) otpRepository.delete(verification);
            return ResponseEntity.status(403).body(Map.of(
                "error", "PHONE_NOT_VERIFIED",
                "message", "Verify the phone number with OTP before registering"
            ));
        }

        // Check phone uniqueness
        if (clientRepository.existsByPhone(cleanPhone)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PHONE_EXISTS",
                "message", "Un compte avec ce numero existe deja"
            ));
        }

        // Check card uniqueness
        String cardHash = sha256(cardDigits);
        if (clientRepository.existsByCardHash(cardHash)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "CARD_EXISTS",
                "message", "Cette carte est deja liee a un autre compte"
            ));
        }

        MobileClient client = new MobileClient();
        client.setPhone(cleanPhone);
        client.setFullName(fullName);
        client.setPinHash(hashPin(pin));
        client.setCardHash(cardHash);

        String last4 = cardDigits.substring(cardDigits.length() - 4);
        client.setMaskedCard("**** **** **** " + last4);

        client = clientRepository.save(client);
        if (verification != null) otpRepository.delete(verification);

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

        if (ValidationUtils.validatePhone(cleanPhone) != null
                || pin.length() < 4 || pin.length() > 12 || !pin.matches("\\d+")) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_PHONE_OR_PIN"));
        }

        var clientOpt = clientRepository.findByPhone(cleanPhone);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "INVALID_CREDENTIALS",
                "message", "Numero ou PIN incorrect"
            ));
        }

        MobileClient client = clientOpt.get();

        if (!verifyPin(pin, client.getPinHash())) {
            int attempts = client.getFailedPinAttempts() == null ? 0 : client.getFailedPinAttempts();
            attempts++;
            client.setFailedPinAttempts(attempts);
            if (attempts >= 5) client.setAccountLocked(true);
            clientRepository.save(client);
            auditLogService.log("MOBILE_LOGIN_FAILED", "mobile", "MobileClient",
                client.getId(), cleanPhone, "FAILED", "Wrong PIN");
            return ResponseEntity.status(401).body(Map.of(
                "error", "INVALID_CREDENTIALS",
                "message", "Numero ou PIN incorrect"
            ));
        }

        if (!Boolean.TRUE.equals(client.getActive()) || Boolean.TRUE.equals(client.getAccountLocked())) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "ACCOUNT_DISABLED",
                "message", "Compte desactive"
            ));
        }

        client.setLastLoginAt(LocalDateTime.now());
        client.setFailedPinAttempts(0);
        client.setAccountLocked(false);
        if (client.getPinHash() != null && !client.getPinHash().contains("$")) {
            client.setPinHash(hashPin(pin));
        }
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

    private String hashPin(String pin) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = derivePin(pin, salt);
        return Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPin(String pin, String stored) {
        if (stored == null || stored.isBlank()) return false;
        // Backward compatibility for accounts created before PBKDF2 migration.
        if (!stored.contains("$")) {
            return MessageDigest.isEqual(sha256(pin).getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String[] parts = stored.split("\\$", 2);
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(expected, derivePin(pin, salt));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] derivePin(String pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120_000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("PIN hashing error", ex);
        } finally {
            spec.clearPassword();
        }
    }

}
