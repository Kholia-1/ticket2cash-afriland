package com.afriland.ticket2cash.fraud;

import com.afriland.ticket2cash.audit.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public List<FraudAlert> getAllAlerts() {
        return fraudAlertRepository.findAll();
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