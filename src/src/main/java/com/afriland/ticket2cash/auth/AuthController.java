package com.afriland.ticket2cash.auth;

import com.afriland.ticket2cash.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private final AppUserRepository userRepository;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final PasswordPolicyService passwordPolicyService;

    public AuthController(AppUserRepository userRepository,
                          AuthService authService,
                          AuditLogService auditLogService,
                          PasswordPolicyService passwordPolicyService) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.passwordPolicyService = passwordPolicyService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {

        AppUser user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null) {
            auditLogService.log(
                    "LOGIN_FAILED",
                    "AUTH",
                    "AppUser",
                    null,
                    request.getUsername(),
                    "FAILED",
                    "Unknown username"
            );

            return ResponseEntity.status(401).body("Invalid username or password");
        }

        if (user.getEnabled() == null || !user.getEnabled()) {
            auditLogService.log(
                    "LOGIN_FAILED_DISABLED",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    user.getUsername(),
                    "FAILED",
                    "User disabled"
            );

            return ResponseEntity.status(403).body("User disabled");
        }

        if (Boolean.TRUE.equals(user.getAccountLocked())) {
            auditLogService.log(
                    "LOGIN_FAILED_LOCKED",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    user.getUsername(),
                    "FAILED",
                    "User account is locked"
            );

            return ResponseEntity.status(423).body("User account locked. Contact ADMIN.");
        }

        if (!authService.verifyPassword(request.getPassword(), user)) {
            int attempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
            attempts++;

            user.setFailedLoginAttempts(attempts);

            if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                if (user.getRole() == UserRole.ADMIN && isLastActiveAdmin(user)) {
                    userRepository.save(user);

                    auditLogService.log(
                            "LOGIN_LOCK_BLOCKED_LAST_ADMIN",
                            "AUTH",
                            "AppUser",
                            user.getId(),
                            user.getUsername(),
                            "FAILED",
                            "Last active ADMIN was not locked despite too many failed login attempts"
                    );

                    return ResponseEntity.status(401).body("Invalid username or password");
                }

                user.setAccountLocked(true);
                user.setLockedAt(LocalDateTime.now());
                userRepository.save(user);

                auditLogService.log(
                        "LOGIN_ACCOUNT_LOCKED",
                        "AUTH",
                        "AppUser",
                        user.getId(),
                        user.getUsername(),
                        "FAILED",
                        "Account locked after " + attempts + " failed login attempts"
                );

                return ResponseEntity.status(423).body("User account locked after too many failed login attempts");
            }

            userRepository.save(user);

            auditLogService.log(
                    "LOGIN_FAILED",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    user.getUsername(),
                    "FAILED",
                    "Invalid password. Failed attempts: " + attempts
            );

            return ResponseEntity.status(401).body("Invalid username or password. Failed attempts: " + attempts);
        }

        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedAt(null);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("AUTH_USER_ID", user.getId());
        session.setAttribute("AUTH_USERNAME", user.getUsername());
        session.setAttribute("AUTH_ROLE", user.getRole().name());
        session.setAttribute("AUTH_FULL_NAME", user.getFullName());
        session.setAttribute("AUTH_MERCHANT_ID", user.getMerchantId());

        authService.updateLastLogin(user);

        auditLogService.log(
                "LOGIN_SUCCESS",
                "AUTH",
                "AppUser",
                user.getId(),
                user.getUsername(),
                "SUCCESS",
                "User logged in"
        );

        return ResponseEntity.ok(toUserMap(user, true));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        String username = "UNKNOWN";

        if (session != null && session.getAttribute("AUTH_USERNAME") != null) {
            username = String.valueOf(session.getAttribute("AUTH_USERNAME"));
            session.invalidate();
        }

        auditLogService.log(
                "LOGOUT",
                "AUTH",
                "Session",
                null,
                username,
                "SUCCESS",
                "User logged out"
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Logged out");
        return response;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        Map<String, Object> response = new LinkedHashMap<>();

        if (session == null || session.getAttribute("AUTH_USER_ID") == null) {
            response.put("authenticated", false);
            return response;
        }

        response.put("authenticated", true);
        response.put("id", session.getAttribute("AUTH_USER_ID"));
        response.put("username", session.getAttribute("AUTH_USERNAME"));
        response.put("role", session.getAttribute("AUTH_ROLE"));
        response.put("merchantId", session.getAttribute("AUTH_MERCHANT_ID"));
        response.put("fullName", session.getAttribute("AUTH_FULL_NAME"));

        return response;
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {

        HttpSession session = httpRequest.getSession(false);

        if (session == null || session.getAttribute("AUTH_USERNAME") == null) {
            return ResponseEntity.status(401).body("Authentication required");
        }

        String username = String.valueOf(session.getAttribute("AUTH_USERNAME"));

        AppUser user = userRepository.findByUsername(username).orElse(null);

        if (user == null && session.getAttribute("AUTH_USER_ID") != null) {
            Long userId = Long.valueOf(String.valueOf(session.getAttribute("AUTH_USER_ID")));
            user = userRepository.findById(userId).orElse(null);
        }

        if (user == null) {
            return ResponseEntity.status(404).body("User not found for session username: " + username);
        }

        if (request.getOldPassword() == null || request.getNewPassword() == null) {
            return ResponseEntity.badRequest().body("Old password and new password are required");
        }

        String policyError = passwordPolicyService.validate(request.getNewPassword(), user.getUsername());

        if (policyError != null) {
            auditLogService.log(
                    "CHANGE_PASSWORD_REJECTED_POLICY",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    user.getUsername(),
                    "FAILED",
                    policyError
            );

            return ResponseEntity.badRequest().body(policyError);
        }

        if (!authService.verifyPassword(request.getOldPassword(), user)) {
            auditLogService.log(
                    "CHANGE_PASSWORD_FAILED",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    user.getUsername(),
                    "FAILED",
                    "Invalid old password"
            );

            return ResponseEntity.status(400).body("Invalid old password");
        }

        AppUser updatedUser = authService.changePassword(user, request.getNewPassword());
        updatedUser.setFailedLoginAttempts(0);
        updatedUser.setAccountLocked(false);
        updatedUser.setLockedAt(null);
        updatedUser = userRepository.save(updatedUser);

        session.setAttribute("AUTH_USER_ID", updatedUser.getId());
        session.setAttribute("AUTH_USERNAME", updatedUser.getUsername());
        session.setAttribute("AUTH_ROLE", updatedUser.getRole().name());
        session.setAttribute("AUTH_FULL_NAME", updatedUser.getFullName());

        auditLogService.log(
                "CHANGE_PASSWORD_SUCCESS",
                "AUTH",
                "AppUser",
                updatedUser.getId(),
                updatedUser.getUsername(),
                "SUCCESS",
                "Password changed by user"
        );

        return ResponseEntity.ok(toUserMap(updatedUser, true));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        List<Map<String, Object>> users = userRepository.findAll()
                .stream()
                .map(user -> toUserMap(user, false))
                .toList();

        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request,
                                        HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body("Username is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Password is required");
        }

        String policyError = passwordPolicyService.validate(request.getPassword(), request.getUsername());

        if (policyError != null) {
            auditLogService.log(
                    "CREATE_USER_REJECTED_POLICY",
                    "AUTH",
                    "AppUser",
                    null,
                    getCurrentUsername(httpRequest),
                    "FAILED",
                    policyError
            );

            return ResponseEntity.badRequest().body(policyError);
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        AppUser user = authService.createUser(
                request.getUsername(),
                request.getFullName(),
                request.getEmail(),
                request.getPassword(),
                request.getRole() != null ? request.getRole() : UserRole.LECTEUR
        );

        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedAt(null);
        user = userRepository.save(user);

        auditLogService.log(
                "CREATE_USER",
                "AUTH",
                "AppUser",
                user.getId(),
                getCurrentUsername(httpRequest),
                "SUCCESS",
                "User created: " + user.getUsername()
        );

        return ResponseEntity.ok(toUserMap(user, false));
    }

    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetUserPasswordByAdmin(@PathVariable Long id,
                                                      @RequestBody AdminResetPasswordRequest request,
                                                      HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        AppUser user = userRepository.findById(id).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            return ResponseEntity.badRequest().body("New password is required");
        }

        String policyError = passwordPolicyService.validate(request.getNewPassword(), user.getUsername());

        if (policyError != null) {
            auditLogService.log(
                    "ADMIN_RESET_PASSWORD_REJECTED_POLICY",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    getCurrentUsername(httpRequest),
                    "FAILED",
                    policyError
            );

            return ResponseEntity.badRequest().body(policyError);
        }

        AppUser updatedUser = authService.changePassword(user, request.getNewPassword());
        updatedUser.setFailedLoginAttempts(0);
        updatedUser.setAccountLocked(false);
        updatedUser.setLockedAt(null);
        updatedUser.setEnabled(true);
        updatedUser = userRepository.save(updatedUser);

        auditLogService.log(
                "ADMIN_RESET_USER_PASSWORD",
                "AUTH",
                "AppUser",
                updatedUser.getId(),
                getCurrentUsername(httpRequest),
                "SUCCESS",
                "Password reset by admin for user: " + updatedUser.getUsername()
        );

        return ResponseEntity.ok(toUserMap(updatedUser, false));
    }

    @PutMapping("/users/{id}/unlock")
    public ResponseEntity<?> unlockUserByAdmin(@PathVariable Long id,
                                               HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        AppUser user = userRepository.findById(id).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }

        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedAt(null);
        user.setEnabled(true);

        AppUser updated = userRepository.save(user);

        auditLogService.log(
                "ADMIN_UNLOCK_USER",
                "AUTH",
                "AppUser",
                updated.getId(),
                getCurrentUsername(request),
                "SUCCESS",
                "User account unlocked by admin: " + updated.getUsername()
        );

        return ResponseEntity.ok(toUserMap(updated, false));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id,
                                            @RequestParam UserRole role,
                                            HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        Long currentUserId = getCurrentUserId(request);

        AppUser user = userRepository.findById(id).orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (currentUserId != null && currentUserId.equals(user.getId()) && role != UserRole.ADMIN) {
            auditLogService.log(
                    "UPDATE_USER_ROLE_BLOCKED_SELF",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    getCurrentUsername(request),
                    "FAILED",
                    "Admin cannot remove own ADMIN role"
            );

            return ResponseEntity.badRequest().body("You cannot remove your own ADMIN role");
        }

        if (user.getRole() == UserRole.ADMIN && role != UserRole.ADMIN && isLastActiveAdmin(user)) {
            auditLogService.log(
                    "UPDATE_USER_ROLE_BLOCKED_LAST_ADMIN",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    getCurrentUsername(request),
                    "FAILED",
                    "Cannot remove role from last active ADMIN"
            );

            return ResponseEntity.badRequest().body("Cannot remove role from the last active ADMIN");
        }

        user.setRole(role);
        AppUser updated = userRepository.save(user);

        auditLogService.log(
                "UPDATE_USER_ROLE",
                "AUTH",
                "AppUser",
                updated.getId(),
                getCurrentUsername(request),
                "SUCCESS",
                "User role changed to " + role
        );

        return ResponseEntity.ok(toUserMap(updated, false));
    }

    @PutMapping("/users/{id}/merchant")
    public ResponseEntity<?> setUserMerchant(@PathVariable Long id,
                                              @RequestParam Long merchantId,
                                              HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.setMerchantId(merchantId);
        AppUser updated = userRepository.save(user);

        auditLogService.log(
                "SET_USER_MERCHANT",
                "AUTH",
                "AppUser",
                updated.getId(),
                getCurrentUsername(request),
                "SUCCESS",
                "User " + updated.getUsername() + " linked to merchantId=" + merchantId
        );

        return ResponseEntity.ok(toUserMap(updated, false));
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<?> updateUserEnabled(@PathVariable Long id,
                                               @RequestParam Boolean enabled,
                                               HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        Long currentUserId = getCurrentUserId(request);

        AppUser user = userRepository.findById(id).orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (currentUserId != null && currentUserId.equals(user.getId()) && Boolean.FALSE.equals(enabled)) {
            auditLogService.log(
                    "UPDATE_USER_STATUS_BLOCKED_SELF",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    getCurrentUsername(request),
                    "FAILED",
                    "Admin cannot disable own account"
            );

            return ResponseEntity.badRequest().body("You cannot disable your own account");
        }

        if (user.getRole() == UserRole.ADMIN
                && Boolean.TRUE.equals(user.getEnabled())
                && Boolean.FALSE.equals(enabled)
                && isLastActiveAdmin(user)) {

            auditLogService.log(
                    "UPDATE_USER_STATUS_BLOCKED_LAST_ADMIN",
                    "AUTH",
                    "AppUser",
                    user.getId(),
                    getCurrentUsername(request),
                    "FAILED",
                    "Cannot disable last active ADMIN"
            );

            return ResponseEntity.badRequest().body("Cannot disable the last active ADMIN");
        }

        user.setEnabled(enabled);
        AppUser updated = userRepository.save(user);

        auditLogService.log(
                "UPDATE_USER_STATUS",
                "AUTH",
                "AppUser",
                updated.getId(),
                getCurrentUsername(request),
                "SUCCESS",
                "User enabled changed to " + enabled
        );

        return ResponseEntity.ok(toUserMap(updated, false));
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("AUTH_USER_ID") == null) {
            return null;
        }

        try {
            return Long.valueOf(String.valueOf(session.getAttribute("AUTH_USER_ID")));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isLastActiveAdmin(AppUser targetUser) {
        long activeAdminCount = userRepository.findAll()
                .stream()
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> !Boolean.TRUE.equals(user.getAccountLocked()))
                .count();

        return targetUser.getRole() == UserRole.ADMIN
                && Boolean.TRUE.equals(targetUser.getEnabled())
                && !Boolean.TRUE.equals(targetUser.getAccountLocked())
                && activeAdminCount <= 1;
    }

    private Map<String, Object> toUserMap(AppUser user, boolean authenticated) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("authenticated", authenticated);
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("fullName", user.getFullName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("enabled", user.getEnabled());
        map.put("failedLoginAttempts", user.getFailedLoginAttempts());
        map.put("accountLocked", user.getAccountLocked());
        map.put("lockedAt", user.getLockedAt());
        map.put("createdAt", user.getCreatedAt());
        map.put("lastLoginAt", user.getLastLoginAt());

        return map;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        return session != null
                && "ADMIN".equals(String.valueOf(session.getAttribute("AUTH_ROLE")));
    }

    private String getCurrentUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("AUTH_USERNAME") == null) {
            return "UNKNOWN";
        }

        return String.valueOf(session.getAttribute("AUTH_USERNAME"));
    }
}