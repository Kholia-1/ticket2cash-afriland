package com.afriland.ticket2cash.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByModuleName(String moduleName);

    List<AuditLog> findByAction(String action);

    List<AuditLog> findByStatus(String status);
}