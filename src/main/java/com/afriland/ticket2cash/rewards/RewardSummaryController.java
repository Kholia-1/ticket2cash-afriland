package com.afriland.ticket2cash.rewards;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only cross-module view of the common reward ledger. */
@RestController
@RequestMapping("/api/rewards")
public class RewardSummaryController {
    private final RewardLedgerRepository ledgerRepository;

    public RewardSummaryController(RewardLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<RewardLedgerEntry> entries = ledgerRepository.findAll();
        BigDecimal cashbackCalculated = entries.stream()
                .filter(this::approved)
                .map(RewardLedgerEntry::getCashbackAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashbackCredited = entries.stream()
                .filter(this::approved)
                .filter(this::credited)
                .map(RewardLedgerEntry::getCashbackAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long points = entries.stream()
                .filter(this::approved)
                .map(RewardLedgerEntry::getPointsAmount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long vouchers = entries.stream()
                .filter(this::approved)
                .filter(e -> e.getVoucherCode() != null && !e.getVoucherCode().isBlank())
                .count();
        long pending = entries.stream().filter(this::pending).count();
        long rejected = entries.stream().filter(e -> !approved(e)
                || "REJECTED".equalsIgnoreCase(value(e.getStatus()))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cashbackCalculated", cashbackCalculated);
        result.put("cashbackCredited", cashbackCredited);
        result.put("loyaltyPointsGenerated", points);
        result.put("vouchersIssued", vouchers);
        result.put("pendingRewards", pending);
        result.put("rejectedRewards", rejected);
        result.put("latestEntries", ledgerRepository.findTop100ByOrderByCalculatedAtDesc()
                .stream().map(this::toDto).toList());
        return result;
    }

    @GetMapping("/ledger")
    public List<Map<String, Object>> ledger() {
        return ledgerRepository.findTop100ByOrderByCalculatedAtDesc()
                .stream().map(this::toDto).toList();
    }

    private boolean approved(RewardLedgerEntry e) {
        return e != null && e.getDecisionCode() == RewardDecisionCode.APPROVED;
    }

    private boolean credited(RewardLedgerEntry e) {
        return e.getPostedAt() != null || "CREDITED".equalsIgnoreCase(value(e.getStatus()))
                || "POSTED".equalsIgnoreCase(value(e.getStatus()));
    }

    private boolean pending(RewardLedgerEntry e) {
        String status = value(e.getStatus()).toUpperCase(Locale.ROOT);
        return "CALCULATED".equals(status) || "PENDING".equals(status) || "PROCESSING".equals(status);
    }

    private String value(String value) { return value == null ? "" : value; }

    private Map<String, Object> toDto(RewardLedgerEntry e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("sourceType", e.getSourceType());
        row.put("sourceRef", e.getSourceRef());
        row.put("customerRef", e.getCustomerRef());
        row.put("maskedCard", safeMaskedCard(e.getMaskedCard()));
        row.put("benefitType", e.getBenefitType());
        row.put("cashbackAmount", e.getCashbackAmount());
        row.put("pointsAmount", e.getPointsAmount());
        row.put("voucherCode", e.getVoucherCode());
        row.put("status", e.getStatus());
        row.put("decisionCode", e.getDecisionCode());
        row.put("rejectionReason", e.getRejectionReason());
        row.put("campaignId", e.getCampaignId());
        row.put("calculatedAt", e.getCalculatedAt());
        row.put("postedAt", e.getPostedAt());
        return row;
    }

    private String safeMaskedCard(String value) {
        if (value == null) return null;
        String compact = value.replaceAll("[ -]", "");
        return compact.matches("\\d{13,19}") ? "****" + compact.substring(compact.length() - 4) : value;
    }
}
