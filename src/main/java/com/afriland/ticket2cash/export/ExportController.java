package com.afriland.ticket2cash.export;

import com.afriland.ticket2cash.audit.AuditLog;
import com.afriland.ticket2cash.audit.AuditLogRepository;
import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.fraud.FraudAlert;
import com.afriland.ticket2cash.fraud.FraudAlertRepository;
import com.afriland.ticket2cash.settings.SystemSettingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final ClaimRepository claimRepository;
    private final CashbackPaymentRepository paymentRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final SystemSettingService settingService;

    public ExportController(ClaimRepository claimRepository,
                            CashbackPaymentRepository paymentRepository,
                            FraudAlertRepository fraudAlertRepository,
                            AuditLogRepository auditLogRepository,
                            AuditLogService auditLogService,
                            SystemSettingService settingService) {
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.settingService = settingService;
    }

    @GetMapping("/claims-csv")
    public ResponseEntity<String> exportClaimsCsv() {
        List<Claim> claims = claimRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append(csvLine(
                "id",
                "claimReference",
                "userId",
                "merchantId",
                "campaignId",
                "ticketId",
                "ticketAmount",
                "cashbackAmount",
                "status",
                "submittedAt"
        ));

        for (Claim claim : claims) {
            csv.append(csvLine(
                    claim.getId(),
                    claim.getClaimReference(),
                    claim.getUserId(),
                    claim.getMerchantId(),
                    claim.getCampaignId(),
                    claim.getTicketId(),
                    claim.getTicketAmount(),
                    claim.getCashbackAmount(),
                    claim.getStatus(),
                    claim.getSubmittedAt()
            ));
        }

        logExport("EXPORT_CLAIMS_CSV", "Claims exported: " + claims.size());

        return csvResponse(csv.toString(), "claims.csv");
    }

    @GetMapping("/payments-csv")
    public ResponseEntity<String> exportPaymentsCsv() {
        List<CashbackPayment> payments = paymentRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append(csvLine(
                "id",
                "paymentReference",
                "claimId",
                "merchantId",
                "campaignId",
                "userId",
                "amount",
                "currency",
                "status",
                "processedAt"
        ));

        for (CashbackPayment payment : payments) {
            csv.append(csvLine(
                    payment.getId(),
                    payment.getPaymentReference(),
                    payment.getClaimId(),
                    payment.getMerchantId(),
                    payment.getCampaignId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    payment.getProcessedAt()
            ));
        }

        logExport("EXPORT_PAYMENTS_CSV", "Payments exported: " + payments.size());

        return csvResponse(csv.toString(), "cashback-payments.csv");
    }

    @GetMapping("/fraud-alerts-csv")
    public ResponseEntity<String> exportFraudAlertsCsv() {
        List<FraudAlert> alerts = fraudAlertRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append(csvLine(
                "id",
                "alertReference",
                "ticketId",
                "claimId",
                "merchantId",
                "userId",
                "riskScore",
                "reason",
                "status",
                "createdAt"
        ));

        for (FraudAlert alert : alerts) {
            csv.append(csvLine(
                    alert.getId(),
                    alert.getAlertReference(),
                    alert.getTicketId(),
                    alert.getClaimId(),
                    alert.getMerchantId(),
                    alert.getUserId(),
                    alert.getRiskScore(),
                    alert.getReason(),
                    alert.getStatus(),
                    alert.getCreatedAt()
            ));
        }

        logExport("EXPORT_FRAUD_ALERTS_CSV", "Fraud alerts exported: " + alerts.size());

        return csvResponse(csv.toString(), "fraud-alerts.csv");
    }

    @GetMapping("/audit-logs-csv")
    public ResponseEntity<String> exportAuditLogsCsv() {
        List<AuditLog> logs = auditLogRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append(csvLine(
                "id",
                "action",
                "moduleName",
                "entityType",
                "entityId",
                "actor",
                "status",
                "message",
                "createdAt"
        ));

        for (AuditLog log : logs) {
            csv.append(csvLine(
                    log.getId(),
                    log.getAction(),
                    log.getModuleName(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.getActor(),
                    log.getStatus(),
                    log.getMessage(),
                    log.getCreatedAt()
            ));
        }

        logExport("EXPORT_AUDIT_LOGS_CSV", "Audit logs exported: " + logs.size());

        return csvResponse(csv.toString(), "audit-logs.csv");
    }

    private ResponseEntity<String> csvResponse(String content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content);
    }

    private String csvLine(Object... values) {
        return Arrays.stream(values)
                .map(this::csvValue)
                .collect(Collectors.joining(",")) + "\n";
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        text = text.replace("\"", "\"\"");

        return "\"" + text + "\"";
    }

    private void logExport(String action, String message) {
        String actor = settingService.getString("DEFAULT_ACTOR", "ADMIN_DEMO");

        auditLogService.log(
                action,
                "EXPORT",
                "CSV",
                null,
                actor,
                "SUCCESS",
                message
        );
    }
}