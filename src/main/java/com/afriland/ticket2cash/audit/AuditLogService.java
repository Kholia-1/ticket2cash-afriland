package com.afriland.ticket2cash.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditLog log(String action,
                        String moduleName,
                        String entityType,
                        Long entityId,
                        String actor,
                        String status,
                        String message) {

        AuditLog log = new AuditLog();

        log.setAction(action);
        log.setModuleName(moduleName);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setActor(sanitize(actor, 120));
        log.setStatus(status);
        log.setMessage(sanitize(message, 1000));

        return auditLogRepository.save(log);
    }

    private String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String sanitized = value.replaceAll("(?i)(password|pin|otp|api[-_ ]?key|card(number|hash)?)[=: ]+[^,; ]+", "$1=[REDACTED]");
        return sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
    }
}
