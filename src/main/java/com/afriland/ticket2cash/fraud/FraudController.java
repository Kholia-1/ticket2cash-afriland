package com.afriland.ticket2cash.fraud;

import com.afriland.ticket2cash.audit.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequestMapping("/api/fraud")
public class FraudController {

    private final FraudAlertRepository fraudAlertRepository;
    private final AuditLogService auditLogService;

    public FraudController(FraudAlertRepository fraudAlertRepository,
                           AuditLogService auditLogService) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/alerts")
    public Page<FraudAlert> getAllAlerts(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return fraudAlertRepository.findAllByOrderByIdDesc(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
    }

    @GetMapping("/alerts/status/{status}")
    public List<FraudAlert> getAlertsByStatus(@PathVariable FraudAlertStatus status) {
        return fraudAlertRepository.findByStatus(status);
    }

    @GetMapping("/alerts/merchant/{merchantId}")
    public List<FraudAlert> getAlertsByMerchant(@PathVariable Long merchantId) {
        return fraudAlertRepository.findByMerchantId(merchantId);
    }

    @PutMapping("/alerts/{id}/status")
    public ResponseEntity<FraudAlert> updateAlertStatus(
            @PathVariable Long id,
            @RequestParam FraudAlertStatus status
    ) {
        return fraudAlertRepository.findById(id)
                .map(alert -> {
                    alert.setStatus(status);
                    FraudAlert updatedAlert = fraudAlertRepository.save(alert);

                    auditLogService.log(
                            "UPDATE_FRAUD_ALERT_STATUS",
                            "FRAUD",
                            "FraudAlert",
                            updatedAlert.getId(),
                            "ADMIN_DEMO",
                            "SUCCESS",
                            "Fraud alert status changed to " + status
                    );

                    return ResponseEntity.ok(updatedAlert);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
