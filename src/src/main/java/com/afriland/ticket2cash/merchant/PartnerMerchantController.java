package com.afriland.ticket2cash.merchant;

import com.afriland.ticket2cash.auth.AppUser;
import com.afriland.ticket2cash.auth.AppUserRepository;
import com.afriland.ticket2cash.auth.AuthService;
import com.afriland.ticket2cash.auth.PasswordPolicyService;
import com.afriland.ticket2cash.auth.UserRole;
import com.afriland.ticket2cash.email.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets a PARTNER (e.g. PlaYce) manage its own sub-partners
 * (e.g. Orange Cameroun) from its own dashboard, and give each
 * sub-partner its own login. Everything is scoped to the logged-in
 * partner's merchant via the session.
 */
@RestController
@RequestMapping("/api/partner")
public class PartnerMerchantController {

    private final MerchantRepository merchantRepository;
    private final AuthService authService;
    private final AppUserRepository userRepository;
    private final PasswordPolicyService passwordPolicyService;
    private final EmailService emailService;

    public PartnerMerchantController(MerchantRepository merchantRepository,
                                     AuthService authService,
                                     AppUserRepository userRepository,
                                     PasswordPolicyService passwordPolicyService,
                                     EmailService emailService) {
        this.merchantRepository = merchantRepository;
        this.authService = authService;
        this.userRepository = userRepository;
        this.passwordPolicyService = passwordPolicyService;
        this.emailService = emailService;
    }

    private Long myMerchantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object mid = session.getAttribute("AUTH_MERCHANT_ID");
        if (mid == null) return null;
        try {
            return Long.valueOf(String.valueOf(mid));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @GetMapping("/sub-merchants")
    public ResponseEntity<?> listSubMerchants(HttpServletRequest request) {
        Long me = myMerchantId(request);
        if (me == null) {
            return ResponseEntity.status(403).body("No merchant linked to this account");
        }
        return ResponseEntity.ok(merchantRepository.findByParentMerchantId(me));
    }

    @PostMapping("/sub-merchants")
    public ResponseEntity<?> createSubMerchant(@RequestBody Merchant merchant, HttpServletRequest request) {
        Long me = myMerchantId(request);
        if (me == null) {
            return ResponseEntity.status(403).body("No merchant linked to this account");
        }
        merchant.setId(null);
        merchant.setParentMerchantId(me);
        return ResponseEntity.ok(merchantRepository.save(merchant));
    }

    /**
     * Create a login (role PARTNER) for one of MY sub-partners, and email
     * the credentials if an address is available and SMTP is configured.
     */
    @PostMapping("/sub-merchants/{subId}/user")
    public ResponseEntity<?> createSubMerchantUser(@PathVariable Long subId,
                                                   @RequestBody Map<String, String> body,
                                                   HttpServletRequest request) {
        Long me = myMerchantId(request);
        if (me == null) {
            return ResponseEntity.status(403).body("No merchant linked to this account");
        }

        Merchant sub = merchantRepository.findById(subId).orElse(null);
        if (sub == null) {
            return ResponseEntity.status(404).body("Sub-partner not found");
        }
        if (sub.getParentMerchantId() == null || !sub.getParentMerchantId().equals(me)) {
            return ResponseEntity.status(403).body("This is not one of your sub-partners");
        }

        String username = body.get("username");
        String password = body.get("password");
        String fullName = body.getOrDefault("fullName", sub.getName());

        String email = body.get("email");
        if (email == null || email.isBlank()) email = sub.getEmail();

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body("Username is required");
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("Password is required");
        }

        String policyError = passwordPolicyService.validate(password, username);
        if (policyError != null) {
            return ResponseEntity.badRequest().body(policyError);
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        if (email != null && !email.isBlank() && userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        AppUser user = authService.createUser(username, fullName, email, password, UserRole.PARTNER);
        user.setMerchantId(subId);
        user = userRepository.save(user);

        boolean emailSent = false;
        String emailError = null;
        if (email != null && !email.isBlank()) {
            try {
                emailService.sendCredentials(email, sub.getName(), username, password, body.get("loginUrl"));
                emailSent = true;
            } catch (Exception e) {
                emailError = e.getMessage();
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", user.getId());
        out.put("username", user.getUsername());
        out.put("role", "PARTNER");
        out.put("merchantId", subId);
        out.put("email", email);
        out.put("emailSent", emailSent);
        if (emailError != null) out.put("emailError", emailError);
        return ResponseEntity.ok(out);
    }
}
