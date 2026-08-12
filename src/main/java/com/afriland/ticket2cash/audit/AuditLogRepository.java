package com.afriland.ticket2cash.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findByModuleName(String moduleName);

    List<AuditLog> findByAction(String action);

    List<AuditLog> findByStatus(String status);
}
