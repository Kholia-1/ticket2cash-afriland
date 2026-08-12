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
        log.setActor(actor);
        log.setStatus(status);
        log.setMessage(message);

        return auditLogRepository.save(log);
    }
}