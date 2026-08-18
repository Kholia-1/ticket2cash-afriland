package com.afriland.ticket2cash.rewards;

import com.afriland.ticket2cash.audit.AuditLogService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** Common, additive reward engine. Existing cashback and loyalty services may adopt it incrementally. */
@Service
public class RewardEngineService {
    private final RewardLedgerRepository ledgerRepository;
    private final AuditLogService auditLogService;

    public RewardEngineService(RewardLedgerRepository ledgerRepository, AuditLogService auditLogService) {
        this.ledgerRepository = ledgerRepository; this.auditLogService = auditLogService;
    }

    @Transactional
    public RewardCalculationResult calculate(RewardCalculationContext context) {
        RewardBenefitType type = context == null ? null : context.getBenefitType();
        audit("REWARD_CALCULATION_STARTED", context, type, null, null, null, null);
        RewardCalculationResult invalid = validate(context, type);
        if (invalid != null) { audit("REWARD_REJECTED", context, type, invalid.getDecisionCode(), invalid.getMessage(), null, null); return invalid; }

        List<RewardLedgerEntry> existing = ledgerRepository.findBySourceTypeAndSourceRefAndBenefitType(
                context.getSourceType(), context.getSourceRef().trim(), type);
        boolean duplicate = existing.stream().anyMatch(entry -> Objects.equals(entry.getCampaignId(), context.getCampaignId()));
        if (duplicate) {
            RewardCalculationResult result = RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_DUPLICATE, "Reward source already processed", type);
            audit("REWARD_DUPLICATE_SKIPPED", context, type, result.getDecisionCode(), result.getMessage(), null, null); return result;
        }

        BigDecimal cashback = null; Long points = null; String voucher = null;
        switch (type) {
            case CASHBACK_PERCENT:
                cashback = context.getAmount().multiply(context.getBenefitValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP); break;
            case CASHBACK_FIXED:
                cashback = context.getBenefitValue().setScale(0, RoundingMode.HALF_UP); break;
            case LOYALTY_POINTS:
                points = points(context); break;
            case TIER_BONUS:
                points = context.getBonusPoints() != null ? context.getBonusPoints() : points(context); break;
            case VOUCHER:
                voucher = context.getVoucherCode().trim(); break;
            default:
                RewardCalculationResult unsupported = RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_UNSUPPORTED_BENEFIT, "Unsupported reward benefit", type);
                audit("REWARD_REJECTED", context, type, unsupported.getDecisionCode(), unsupported.getMessage(), null, null); return unsupported;
        }

        RewardLedgerEntry entry = new RewardLedgerEntry();
        entry.setSourceType(context.getSourceType()); entry.setSourceRef(context.getSourceRef().trim());
        entry.setCustomerRef(context.getCustomerRef()); entry.setMaskedCard(safeMaskedCard(context.getMaskedCard())); entry.setCardHash(context.getCardHash());
        entry.setMerchantId(context.getMerchantId()); entry.setMerchantName(context.getMerchantName()); entry.setCampaignId(context.getCampaignId());
        entry.setBenefitType(type); entry.setCashbackAmount(cashback); entry.setPointsAmount(points); entry.setVoucherCode(voucher);
        entry.setDecisionCode(RewardDecisionCode.APPROVED); entry.setStatus("CALCULATED"); entry.setDetailsJson(context.getDetailsJson());
        try { entry = ledgerRepository.save(entry); }
        catch (DataIntegrityViolationException duplicateException) {
            RewardCalculationResult result = RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_DUPLICATE, "Reward source already processed", type);
            audit("REWARD_DUPLICATE_SKIPPED", context, type, result.getDecisionCode(), result.getMessage(), null, null); return result;
        }
        audit("REWARD_CALCULATED", context, type, RewardDecisionCode.APPROVED, null, cashback, points);
        audit("REWARD_LEDGER_CREATED", context, type, RewardDecisionCode.APPROVED, null, cashback, points);
        return RewardCalculationResult.approved(type, cashback, points, voucher, entry);
    }

    public RewardCalculationResult calculate(RewardCalculationContext context, RewardBenefitType benefitType) {
        if (context != null) context.setBenefitType(benefitType);
        return calculate(context);
    }

    private RewardCalculationResult validate(RewardCalculationContext context, RewardBenefitType type) {
        if (context == null || context.getSourceType() == null || context.getSourceRef() == null || context.getSourceRef().isBlank())
            return RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_MISSING_SOURCE, "sourceType and sourceRef are required", type);
        if (context.getAmount() == null || context.getAmount().signum() <= 0)
            return RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_INVALID_AMOUNT, "Amount must be greater than zero", type);
        if ((context.getCustomerRef() == null || context.getCustomerRef().isBlank())
                && (context.getCardHash() == null || context.getCardHash().isBlank()))
            return RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_MISSING_CUSTOMER, "customerRef or cardHash is required", type);
        if (type == null) return RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_MISSING_BENEFIT_TYPE, "benefitType is required", null);
        if ((type == RewardBenefitType.CASHBACK_PERCENT || type == RewardBenefitType.CASHBACK_FIXED)
                && (context.getBenefitValue() == null || context.getBenefitValue().signum() <= 0))
            return RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_INVALID_VALUE, "Cashback value must be greater than zero", type);
        if (type == RewardBenefitType.LOYALTY_POINTS && (context.getPointsPer1000() == null || context.getPointsPer1000() <= 0))
            return RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_INVALID_VALUE, "Points rate must be greater than zero", type);
        if (type == RewardBenefitType.TIER_BONUS && ((context.getBonusPoints() == null || context.getBonusPoints() <= 0)
                && (context.getPointsPer1000() == null || context.getPointsPer1000() <= 0)))
            return RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_INVALID_VALUE, "Tier bonus must be greater than zero", type);
        if (type == RewardBenefitType.VOUCHER && (context.getVoucherCode() == null || context.getVoucherCode().isBlank()))
            return RewardCalculationResult.rejected(RewardDecisionCode.REJECTED_MISSING_VOUCHER_CODE, "voucherCode is required", type);
        return null;
    }

    private Long points(RewardCalculationContext context) {
        return context.getAmount().divide(BigDecimal.valueOf(1000), 6, RoundingMode.DOWN)
                .multiply(BigDecimal.valueOf(context.getPointsPer1000())).setScale(0, RoundingMode.DOWN).longValue();
    }

    private void audit(String action, RewardCalculationContext context, RewardBenefitType type, RewardDecisionCode code, String reason, BigDecimal cashback, Long points) {
        if (context == null) return;
        String details = "sourceType=" + context.getSourceType() + " | sourceRef=" + safe(context.getSourceRef())
                + " | benefitType=" + type + " | amount=" + context.getAmount() + " | cashback=" + (cashback == null ? "—" : cashback)
                + " | points=" + (points == null ? "—" : points)
                + " | campaignId=" + context.getCampaignId() + " | decisionCode=" + code
                + (reason == null ? "" : " | reason=" + safe(reason));
        auditLogService.log(action, "REWARDS", "RewardLedgerEntry", null, context.getSourceRef(), code == RewardDecisionCode.APPROVED ? "SUCCESS" : "REJECTED", details);
    }
    private String safe(String value) { return value == null ? "—" : value.replaceAll("[\\r\\n]", " "); }
    /** Defensive boundary: never persist a full PAN even if a caller mislabels it as maskedCard. */
    private String safeMaskedCard(String value) {
        if (value == null) return null;
        String compact = value.replaceAll("[ -]", "");
        if (compact.matches("\\d{13,19}")) return "****" + compact.substring(compact.length() - 4);
        return value;
    }
}
