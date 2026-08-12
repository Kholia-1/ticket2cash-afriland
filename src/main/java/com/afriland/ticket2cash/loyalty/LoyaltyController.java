package com.afriland.ticket2cash.loyalty;

import com.afriland.ticket2cash.audit.AuditLogService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST façade for the Afriland loyalty cashback engine.
 *
 * <p>Endpoints are grouped under {@code /api/loyalty}:
 * <ul>
 *   <li>{@code /rules}    — CRUD on cashback rules</li>
 *   <li>{@code /batches}  — upload, list, get, delete a batch</li>
 *   <li>{@code /batches/{id}/calculate?ruleId=} — apply a rule and preview</li>
 *   <li>{@code /batches/{id}/approve} — approve for crediting</li>
 *   <li>{@code /batches/{id}/reject}  — cancel</li>
 *   <li>{@code /batches/{id}/credit}  — trigger Core Banking credits</li>
 *   <li>{@code /batches/{id}/results} — per-client cashback</li>
 *   <li>{@code /clients}  — enrolled client directory (searchable)</li>
 *   <li>{@code /stats}    — dashboard totals</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyController {

    private final LoyaltyClientRepository clientRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyRuleRepository ruleRepository;
    private final LoyaltyBatchRepository batchRepository;
    private final LoyaltyResultRepository resultRepository;
    private final LoyaltyImportService importService;
    private final LoyaltyCalculatorService calculatorService;
    private final LoyaltyCreditService creditService;
    private final AuditLogService auditLogService;

    public LoyaltyController(LoyaltyClientRepository clientRepository,
                             LoyaltyTransactionRepository transactionRepository,
                             LoyaltyRuleRepository ruleRepository,
                             LoyaltyBatchRepository batchRepository,
                             LoyaltyResultRepository resultRepository,
                             LoyaltyImportService importService,
                             LoyaltyCalculatorService calculatorService,
                             LoyaltyCreditService creditService,
                             AuditLogService auditLogService) {
        this.clientRepository = clientRepository;
        this.transactionRepository = transactionRepository;
        this.ruleRepository = ruleRepository;
        this.batchRepository = batchRepository;
        this.resultRepository = resultRepository;
        this.importService = importService;
        this.calculatorService = calculatorService;
        this.creditService = creditService;
        this.auditLogService = auditLogService;
    }

    // ============================================================ RULES

    @GetMapping("/rules")
    public List<LoyaltyRule> listRules() { return ruleRepository.findAll(); }

    @GetMapping("/rules/{id}")
    public ResponseEntity<LoyaltyRule> getRule(@PathVariable Long id) {
        return ruleRepository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    public LoyaltyRule createRule(@RequestBody LoyaltyRule rule) {
        LoyaltyRule saved = ruleRepository.save(rule);
        audit("CREATE_LOYALTY_RULE", saved.getId(), "Rule created: " + saved.getName());
        return saved;
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<LoyaltyRule> updateRule(@PathVariable Long id, @RequestBody LoyaltyRule patch) {
        return ruleRepository.findById(id).map(existing -> {
            if (patch.getName() != null) existing.setName(patch.getName());
            if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
            if (patch.getType() != null) existing.setType(patch.getType());
            if (patch.getPercentage() != null) existing.setPercentage(patch.getPercentage());
            if (patch.getTiersJson() != null) existing.setTiersJson(patch.getTiersJson());
            if (patch.getMinTransactionAmount() != null) existing.setMinTransactionAmount(patch.getMinTransactionAmount());
            if (patch.getMaxCashbackPerClient() != null) existing.setMaxCashbackPerClient(patch.getMaxCashbackPerClient());
            if (patch.getCategoryFilter() != null) existing.setCategoryFilter(patch.getCategoryFilter());
            if (patch.getTierFilter() != null) existing.setTierFilter(patch.getTierFilter());
            if (patch.getMinPeriodVolume() != null) existing.setMinPeriodVolume(patch.getMinPeriodVolume());
            if (patch.getEntityTypeFilter() != null) existing.setEntityTypeFilter(patch.getEntityTypeFilter());
            if (patch.getMinTransactionCount() != null) existing.setMinTransactionCount(patch.getMinTransactionCount());
            if (patch.getMinAvgTransactionValue() != null) existing.setMinAvgTransactionValue(patch.getMinAvgTransactionValue());
            if (patch.getMinAccountAgeMonths() != null) existing.setMinAccountAgeMonths(patch.getMinAccountAgeMonths());
            if (patch.getActive() != null) existing.setActive(patch.getActive());
            LoyaltyRule saved = ruleRepository.save(existing);
            audit("UPDATE_LOYALTY_RULE", saved.getId(), "Rule updated: " + saved.getName());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Long id) {
        return ruleRepository.findById(id).map(rule -> {
            rule.setActive(false);
            ruleRepository.save(rule);
            audit("DEACTIVATE_LOYALTY_RULE", id, "Rule deactivated: " + rule.getName());
            return ResponseEntity.ok(Map.of("message", "Règle désactivée"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ============================================================ BATCHES

    @GetMapping("/batches")
    public List<LoyaltyBatch> listBatches() { return batchRepository.findAllByOrderByCreatedAtDesc(); }

    @GetMapping("/batches/{id}")
    public ResponseEntity<LoyaltyBatch> getBatch(@PathVariable Long id) {
        return batchRepository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/batches/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadBatch(@RequestParam("file") MultipartFile file,
                                         @RequestParam("name") String name) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier requis"));
        }
        LoyaltyBatch batch = new LoyaltyBatch();
        batch.setName(name == null || name.isBlank() ? file.getOriginalFilename() : name);
        batch.setSourceFilename(file.getOriginalFilename());
        batch.setCreatedBy("ADMIN"); // TODO wire real principal
        batch = batchRepository.save(batch);

        try {
            batch = importService.importFile(batch, file.getOriginalFilename(), file.getBytes());
        } catch (Exception e) {
            batch.setStatus(LoyaltyBatchStatus.FAILED);
            batch.setNote("Upload failed: " + e.getMessage());
            batchRepository.save(batch);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Import failed",
                    "batchId", batch.getId(),
                    "details", e.getMessage()));
        }

        audit("UPLOAD_LOYALTY_BATCH", batch.getId(),
                String.format("Batch '%s' uploaded: %d rows parsed, %d failed",
                        batch.getName(), batch.getParsedRows(), batch.getFailedRows()));
        return ResponseEntity.ok(batch);
    }

    @PostMapping("/batches/{id}/calculate")
    public ResponseEntity<?> calculate(@PathVariable Long id, @RequestParam Long ruleId) {
        try {
            LoyaltyBatch batch = calculatorService.calculate(id, ruleId);
            audit("CALCULATE_LOYALTY_BATCH", id,
                    String.format("Rule=%d → total=%s FCFA, %d clients",
                            ruleId, batch.getTotalCashback().toPlainString(), batch.getClientCount()));
            return ResponseEntity.ok(batch);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/batches/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestParam(defaultValue = "ADMIN") String approver) {
        try {
            LoyaltyBatch batch = creditService.approve(id, approver);
            audit("APPROVE_LOYALTY_BATCH", id, "Batch approved by " + approver);
            return ResponseEntity.ok(batch);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/batches/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body == null ? null : body.get("reason");
            LoyaltyBatch batch = creditService.reject(id, reason);
            audit("REJECT_LOYALTY_BATCH", id, "Batch rejected: " + reason);
            return ResponseEntity.ok(batch);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/batches/{id}/credit")
    public ResponseEntity<?> credit(@PathVariable Long id) {
        try {
            LoyaltyBatch batch = creditService.credit(id);
            audit("CREDIT_LOYALTY_BATCH", id, "Batch credited: " + batch.getNote());
            return ResponseEntity.ok(batch);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/batches/{id}")
    public ResponseEntity<?> deleteBatch(@PathVariable Long id) {
        return batchRepository.findById(id).map(batch -> {
            if (batch.getStatus() == LoyaltyBatchStatus.CREDITED) {
                return ResponseEntity.status(400).body(
                        (Object) Map.of("error", "Cannot delete a credited batch."));
            }
            transactionRepository.deleteByBatchId(id);
            resultRepository.deleteByBatchId(id);
            batchRepository.deleteById(id);
            audit("DELETE_LOYALTY_BATCH", id, "Batch deleted: " + batch.getName());
            return ResponseEntity.ok((Object) Map.of("message", "Batch supprimé"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/batches/{id}/results")
    public List<LoyaltyResult> batchResults(@PathVariable Long id) {
        return resultRepository.findByBatchIdOrderByCashbackAmountDesc(id);
    }

    @GetMapping("/batches/{id}/transactions")
    public List<LoyaltyTransaction> batchTransactions(@PathVariable Long id) {
        return transactionRepository.findByBatchId(id);
    }

    // ============================================================ CLIENTS

    @GetMapping("/clients")
    public List<LoyaltyClient> listClients() { return clientRepository.findAll(); }

    /**
     * Top clients by lifetime volume — powers the Business Dashboard.
     * @param type INDIVIDUAL, COMPANY, or ALL (default)
     * @param limit max rows to return, defaulted to 10, capped at 50
     */
    @GetMapping("/clients/top")
    public List<LoyaltyClient> topClients(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "10") int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);
        org.springframework.data.domain.Pageable page =
                org.springframework.data.domain.PageRequest.of(0, capped);
        if ("ALL".equalsIgnoreCase(type)) {
            return clientRepository.findTop(page);
        }
        return clientRepository.findTopByEntityType(type.toUpperCase(java.util.Locale.ROOT), page);
    }

    @PostMapping("/clients")
    public LoyaltyClient createClient(@RequestBody LoyaltyClient c) {
        LoyaltyClient saved = clientRepository.save(c);
        audit("CREATE_LOYALTY_CLIENT", saved.getId(), "Client enrolled: " + saved.getAccountNumber());
        return saved;
    }

    @PutMapping("/clients/{id}")
    public ResponseEntity<LoyaltyClient> updateClient(@PathVariable Long id, @RequestBody LoyaltyClient patch) {
        return clientRepository.findById(id).map(c -> {
            if (patch.getFullName() != null) c.setFullName(patch.getFullName());
            if (patch.getPhone() != null) c.setPhone(patch.getPhone());
            if (patch.getEmail() != null) c.setEmail(patch.getEmail());
            if (patch.getCardNumber() != null) c.setCardNumber(patch.getCardNumber());
            if (patch.getTier() != null) c.setTier(patch.getTier());
            if (patch.getEntityType() != null) c.setEntityType(patch.getEntityType());
            if (patch.getCity() != null) c.setCity(patch.getCity());
            if (patch.getBranch() != null) c.setBranch(patch.getBranch());
            if (patch.getAccountOpenedDate() != null) c.setAccountOpenedDate(patch.getAccountOpenedDate());
            return ResponseEntity.ok(clientRepository.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/clients/{id}")
    public ResponseEntity<?> deleteClient(@PathVariable Long id) {
        if (!clientRepository.existsById(id)) return ResponseEntity.notFound().build();
        clientRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Client supprimé"));
    }

    // ============================================================ STATS

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> s = new HashMap<>();
        s.put("clients", clientRepository.count());
        s.put("individuals", clientRepository.countByEntityType("INDIVIDUAL"));
        s.put("companies", clientRepository.countByEntityType("COMPANY"));
        s.put("rules", ruleRepository.count());
        s.put("batches", batchRepository.count());

        long credited = batchRepository.countByStatus(LoyaltyBatchStatus.CREDITED);
        long pending = batchRepository.countByStatus(LoyaltyBatchStatus.CALCULATED)
                + batchRepository.countByStatus(LoyaltyBatchStatus.APPROVED);
        s.put("creditedBatches", credited);
        s.put("pendingBatches", pending);

        // Sum lifetime cashback across all clients
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (LoyaltyClient c : clientRepository.findAll()) {
            if (c.getLifetimeCashback() != null) totalPaid = totalPaid.add(c.getLifetimeCashback());
        }
        s.put("totalPaidCashback", totalPaid);
        return s;
    }

    // ============================================================ helpers

    private void audit(String action, Long id, String details) {
        try {
            auditLogService.log(action, "LOYALTY", "Loyalty", id, "ADMIN", "SUCCESS", details);
        } catch (Exception ignored) {
            // audit is best-effort; never break the primary flow
        }
    }
}
