package com.afriland.ticket2cash.audit;

import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogRepository auditLogRepository,
                              AuditLogService auditLogService) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Page<AuditLogResponse> getAllLogs(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))).map(AuditLogResponse::from);
    }

    @GetMapping("/module/{moduleName}")
    public List<AuditLogResponse> getLogsByModule(@PathVariable String moduleName) {
        return auditLogRepository.findByModuleName(moduleName).stream().map(AuditLogResponse::from).toList();
    }

    @GetMapping("/action/{action}")
    public List<AuditLogResponse> getLogsByAction(@PathVariable String action) {
        return auditLogRepository.findByAction(action).stream().map(AuditLogResponse::from).toList();
    }

    @GetMapping("/status/{status}")
    public List<AuditLogResponse> getLogsByStatus(@PathVariable String status) {
        return auditLogRepository.findByStatus(status).stream().map(AuditLogResponse::from).toList();
    }

    @PostMapping("/test")
    public AuditLogResponse createTestLog() {
        return AuditLogResponse.from(auditLogService.log(
                "TEST_AUDIT",
                "AUDIT",
                "SYSTEM",
                null,
                "ADMIN_DEMO",
                "SUCCESS",
                "Test audit log created from API"
        ));
    }
}
