package com.afriland.ticket2cash.apikey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ADMIN functionality: issue and revoke API keys for supermarkets/partners.
 * The plain key is returned ONLY once, at creation.
 */
@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {

    private final ApiKeyRepository repository;
    private final ApiKeyService service;

    public ApiKeyController(ApiKeyRepository repository, ApiKeyService service) {
        this.repository = repository;
        this.service = service;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && "ADMIN".equals(String.valueOf(s.getAttribute("AUTH_ROLE")));
    }

    private Map<String, Object> toMap(ApiKey k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", k.getId());
        m.put("name", k.getName());
        m.put("merchantId", k.getMerchantId());
        m.put("keyPrefix", k.getKeyPrefix());
        m.put("active", k.isActive());
        m.put("createdAt", k.getCreatedAt());
        m.put("lastUsedAt", k.getLastUsedAt());
        m.put("callCount", k.getCallCount());
        return m;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).body("Admin only");
        List<Map<String, Object>> out = new ArrayList<>();
        for (ApiKey k : repository.findAll()) out.add(toMap(k));
        return ResponseEntity.ok(out);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).body("Admin only");

        String name = body.get("name") == null ? null : String.valueOf(body.get("name")).trim();
        if (name == null || name.isEmpty()) return ResponseEntity.badRequest().body("Name is required");

        Long merchantId = null;
        if (body.get("merchantId") != null) {
            try { merchantId = Long.valueOf(String.valueOf(body.get("merchantId"))); } catch (Exception ignored) { }
        }

        String rawKey = service.generateRawKey();
        ApiKey k = new ApiKey();
        k.setName(name);
        k.setMerchantId(merchantId);
        k.setKeyHash(service.hash(rawKey));
        k.setKeyPrefix(rawKey.substring(0, 12));
        k.setActive(true);
        k = repository.save(k);

        Map<String, Object> out = toMap(k);
        out.put("apiKey", rawKey); // shown ONCE
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{id}/revoke")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).body("Admin only");
        ApiKey k = repository.findById(id).orElse(null);
        if (k == null) return ResponseEntity.status(404).body("Not found");
        k.setActive(false);
        repository.save(k);
        return ResponseEntity.ok(toMap(k));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).body("Admin only");
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }
}
