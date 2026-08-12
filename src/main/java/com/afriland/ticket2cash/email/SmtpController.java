package com.afriland.ticket2cash.email;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/smtp")
public class SmtpController {

    private final SmtpConfigRepository repo;
    private final EmailService emailService;

    public SmtpController(SmtpConfigRepository repo, EmailService emailService) {
        this.repo = repo;
        this.emailService = emailService;
    }

    private boolean isAdmin(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        return s != null && "ADMIN".equals(String.valueOf(s.getAttribute("AUTH_ROLE")));
    }

    private SmtpConfig getOrNew() {
        return repo.findAll().stream().findFirst().orElseGet(SmtpConfig::new);
    }

    private Map<String, Object> toMap(SmtpConfig c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("host", c.getHost());
        out.put("port", c.getPort());
        out.put("username", c.getUsername());
        out.put("hasPassword", c.getPassword() != null && !c.getPassword().isBlank());
        out.put("fromAddress", c.getFromAddress());
        out.put("fromName", c.getFromName());
        out.put("useTls", c.getUseTls() != null ? c.getUseTls() : Boolean.TRUE);
        out.put("enabled", c.getEnabled() != null ? c.getEnabled() : Boolean.FALSE);
        return out;
    }

    @GetMapping
    public ResponseEntity<?> get(HttpServletRequest req) {
        if (!isAdmin(req)) return ResponseEntity.status(403).body("ADMIN role required");
        return ResponseEntity.ok(toMap(getOrNew()));
    }

    @PutMapping
    public ResponseEntity<?> update(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        if (!isAdmin(req)) return ResponseEntity.status(403).body("ADMIN role required");
        SmtpConfig c = getOrNew();
        c.setHost(str(body.get("host")));
        c.setPort(intOrNull(body.get("port")));
        c.setUsername(str(body.get("username")));
        Object pw = body.get("password");
        if (pw != null && !String.valueOf(pw).isBlank()) c.setPassword(String.valueOf(pw));
        c.setFromAddress(str(body.get("fromAddress")));
        c.setFromName(str(body.get("fromName")));
        c.setUseTls(bool(body.get("useTls")));
        c.setEnabled(bool(body.get("enabled")));
        repo.save(c);
        return ResponseEntity.ok(toMap(c));
    }

    @PostMapping("/test")
    public ResponseEntity<?> test(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!isAdmin(req)) return ResponseEntity.status(403).body("ADMIN role required");
        String to = body.get("to");
        if (to == null || to.isBlank()) return ResponseEntity.badRequest().body("Recipient (to) is required");
        try {
            emailService.send(to, "Test SMTP Ticket2Cash",
                    "Ceci est un email de test. Votre configuration SMTP fonctionne.");
            return ResponseEntity.ok(Map.of("sent", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Send failed: " + e.getMessage());
        }
    }

    @PostMapping("/send-credentials")
    public ResponseEntity<?> sendCredentials(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!isAdmin(req)) return ResponseEntity.status(403).body("ADMIN role required");
        String to = body.get("to");
        if (to == null || to.isBlank()) return ResponseEntity.badRequest().body("Recipient (to) is required");
        try {
            emailService.sendCredentials(to, body.get("name"), body.get("username"),
                    body.get("password"), body.get("loginUrl"));
            return ResponseEntity.ok(Map.of("sent", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Send failed: " + e.getMessage());
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Integer intOrNull(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try { return Integer.valueOf(String.valueOf(o).trim()); } catch (NumberFormatException e) { return null; }
    }
    private static Boolean bool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean) return (Boolean) o;
        return "true".equalsIgnoreCase(String.valueOf(o));
    }
}
