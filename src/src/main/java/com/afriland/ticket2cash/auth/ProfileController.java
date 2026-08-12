package com.afriland.ticket2cash.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets the currently logged-in user update their own name / email.
 */
@RestController
@RequestMapping("/api/auth")
public class ProfileController {

    private final AppUserRepository userRepository;

    public ProfileController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body, HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("AUTH_USER_ID") == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        Long uid = Long.valueOf(String.valueOf(s.getAttribute("AUTH_USER_ID")));
        AppUser u = userRepository.findById(uid).orElse(null);
        if (u == null) return ResponseEntity.status(404).body("User not found");

        if (body.get("fullName") != null) u.setFullName(body.get("fullName"));
        if (body.get("email") != null) u.setEmail(body.get("email"));
        u = userRepository.save(u);
        s.setAttribute("AUTH_FULL_NAME", u.getFullName());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.getId());
        out.put("username", u.getUsername());
        out.put("fullName", u.getFullName());
        out.put("email", u.getEmail());
        return ResponseEntity.ok(out);
    }
}
