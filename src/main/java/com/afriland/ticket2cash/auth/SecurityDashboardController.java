package com.afriland.ticket2cash.auth;

import com.afriland.ticket2cash.audit.AuditLog;
import com.afriland.ticket2cash.audit.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/security-dashboard")
public class SecurityDashboardController {

    private final AppUserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public SecurityDashboardController(AppUserRepository userRepository,
                                       AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSecuritySummary(HttpServletRequest request) {

        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body("ADMIN role required");
        }

        List<AppUser> users = userRepository.findAll();
        List<AuditLog> logs = auditLogRepository.findAll();

        long totalUsers = users.size();

        long activeUsers = users.stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .count();

        long disabledUsers = users.stream()
                .filter(user -> !Boolean.TRUE.equals(user.getEnabled()))
                .count();

        long lockedUsers = users.stream()
                .filter(user -> Boolean.TRUE.equals(user.getAccountLocked()))
                .count();

        long activeAdmins = users.stream()
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> !Boolean.TRUE.equals(user.getAccountLocked()))
                .count();

        long operators = users.stream()
                .filter(user -> user.getRole() == UserRole.OPERATEUR)
                .count();

        long readers = users.stream()
                .filter(user -> user.getRole() == UserRole.LECTEUR)
                .count();

        long loginSuccess = countAction(logs, "LOGIN_SUCCESS");
        long loginFailed = countAction(logs, "LOGIN_FAILED");
        long loginFailedLocked = countAction(logs, "LOGIN_FAILED_LOCKED");
        long loginAccountLocked = countAction(logs, "LOGIN_ACCOUNT_LOCKED");
        long adminUnlocks = countAction(logs, "ADMIN_UNLOCK_USER");
        long adminPasswordResets = countAction(logs, "ADMIN_RESET_USER_PASSWORD");

        List<Map<String, Object>> recentSecurityLogs = logs.stream()
                .filter(this::isSecurityLog)
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(20)
                .map(this::toLogMap)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("totalUsers", totalUsers);
        response.put("activeUsers", activeUsers);
        response.put("disabledUsers", disabledUsers);
        response.put("lockedUsers", lockedUsers);
        response.put("activeAdmins", activeAdmins);
        response.put("operators", operators);
        response.put("readers", readers);

        response.put("loginSuccess", loginSuccess);
        response.put("loginFailed", loginFailed);
        response.put("loginFailedLocked", loginFailedLocked);
        response.put("loginAccountLocked", loginAccountLocked);
        response.put("adminUnlocks", adminUnlocks);
        response.put("adminPasswordResets", adminPasswordResets);

        response.put("recentSecurityLogs", recentSecurityLogs);

        return ResponseEntity.ok(response);
    }

    private long countAction(List<AuditLog> logs, String action) {
        return logs.stream()
                .filter(log -> Objects.equals(action, log.getAction()))
                .count();
    }

    private boolean isSecurityLog(AuditLog log) {
        if (log.getAction() == null) {
            return false;
        }

        String action = log.getAction();

        return action.startsWith("LOGIN")
                || action.contains("PASSWORD")
                || action.contains("USER")
                || action.contains("ROLE")
                || action.contains("ADMIN")
                || action.contains("UNLOCK")
                || action.contains("BLOCKED");
    }

    private Map<String, Object> toLogMap(AuditLog log) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("id", log.getId());
        map.put("action", log.getAction());
        map.put("module", log.getModuleName());
        map.put("entityType", log.getEntityType());
        map.put("entityId", log.getEntityId());
        map.put("actor", log.getActor());
        map.put("status", log.getStatus());
        map.put("message", log.getMessage());
        map.put("createdAt", log.getCreatedAt());

        return map;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        return session != null
                && "ADMIN".equals(String.valueOf(session.getAttribute("AUTH_ROLE")));
    }
}