package com.afriland.ticket2cash.cashback.engine;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.cashback.CashbackPaymentStatus;
import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.campaign.CampaignTriggerType;
import com.afriland.ticket2cash.product.CashbackType;
import com.afriland.ticket2cash.rewards.RewardBenefitType;
import com.afriland.ticket2cash.rewards.RewardCalculationContext;
import com.afriland.ticket2cash.rewards.RewardEngineService;
import com.afriland.ticket2cash.rewards.RewardSourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class TransactionCashbackProcessingService {

    private final CampaignRepository campaignRepository;
    private final CashbackCalculationService calculationService;
    private final CashbackPaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final RewardEngineService rewardEngineService;

    /** Kept for source compatibility with callers that construct this service directly. */
    public TransactionCashbackProcessingService(CampaignRepository campaignRepository,
                                                CashbackCalculationService calculationService,
                                                CashbackPaymentRepository paymentRepository,
                                                AuditLogService auditLogService) {
        this(campaignRepository, calculationService, paymentRepository, auditLogService, null);
    }

    @Autowired
    public TransactionCashbackProcessingService(CampaignRepository campaignRepository,
                                                CashbackCalculationService calculationService,
                                                CashbackPaymentRepository paymentRepository,
                                                AuditLogService auditLogService,
                                                RewardEngineService rewardEngineService) {
        this.campaignRepository = campaignRepository;
        this.calculationService = calculationService;
        this.paymentRepository = paymentRepository;
        this.auditLogService = auditLogService;
        this.rewardEngineService = rewardEngineService;
    }

    @Transactional
    public CashbackDecision process(CashbackTransactionRequest request) {
        if (request == null || request.getTransactionRef() == null
                || request.getTransactionRef().isBlank()) {
            return CashbackDecision.rejected(CashbackDecisionCode.REJECTED_TRANSACTION_REF_REQUIRED,
                    "Transaction reference is required", request == null ? null : request.getAmount(),
                    null, null, request == null ? null : request.getMerchantId(), null);
        }
        if (paymentRepository.findByTransactionRef(request.getTransactionRef().trim()).isPresent()) {
            return CashbackDecision.rejected(CashbackDecisionCode.REJECTED_DUPLICATE_TICKET,
                    "Transaction has already been processed", request.getAmount(), null,
                    null, request.getMerchantId(), null);
        }

        Campaign campaign = findBestCampaign(request);
        CashbackDecision decision = calculationService.calculateFromTransaction(campaign, request);
        if (!decision.isEligible()) return decision;

        CashbackPayment payment = new CashbackPayment();
        payment.setTransactionRef(request.getTransactionRef().trim());
        payment.setPaymentReference("TXN-PAY-" + request.getTransactionRef().trim());
        payment.setMerchantId(request.getMerchantId());
        payment.setCampaignId(campaign.getId());
        payment.setUserId(request.getCustomerRef());
        payment.setCustomerRef(request.getCustomerRef());
        payment.setAmount(decision.getFinalCashback());
        payment.setCurrency(request.getCurrency() == null || request.getCurrency().isBlank()
                ? "FCFA" : request.getCurrency());
        payment.setCardHash(request.getCardHash());
        payment.setMaskedCard(request.getMaskedCard());
        payment.setCardBin(request.getCardBin());
        payment.setTransactionDateTime(request.getTransactionDateTime());
        payment.setCampaignCashbackAmount(decision.getCampaignCashbackAmount());
        payment.setLoyaltyBonusEnabled(decision.isLoyaltyBonusEnabled());
        payment.setLoyaltyTierName(decision.getLoyaltyTierName());
        payment.setLoyaltyBonusPercent(decision.getLoyaltyBonusPercent());
        payment.setLoyaltyBonusAmount(decision.getLoyaltyBonusAmount());
        payment.setFinalCashbackAmount(decision.getFinalCashbackAmount());
        payment.setStatus(CashbackPaymentStatus.PENDING);

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException duplicate) {
            return CashbackDecision.rejected(CashbackDecisionCode.REJECTED_DUPLICATE_TICKET,
                    "Transaction has already been processed", request.getAmount(), null,
                    campaign.getId(), request.getMerchantId(), null);
        }

        // Phase 1 of the consolidation: preserve the existing cashback decision
        // and payment flow, while recording the same approved benefit in the
        // common reward ledger. A ledger failure must not roll back a validated
        // legacy cashback payment during the migration period.
        if (rewardEngineService != null) {
            try { rewardEngineService.calculate(rewardContext(request, campaign, decision)); }
            catch (RuntimeException ignored) { /* legacy cashback remains authoritative */ }
        }

        auditLogService.log("TRANSACTION_CASHBACK_CREATED", "CASHBACK", "CashbackPayment",
                payment.getId(), request.getTransactionRef(), "SUCCESS",
                "Cashback reserved for card transaction");
        return decision;
    }

    private RewardCalculationContext rewardContext(CashbackTransactionRequest request, Campaign campaign,
                                                   CashbackDecision decision) {
        RewardCalculationContext context = new RewardCalculationContext();
        context.setSourceType(RewardSourceType.CARD_TRANSACTION);
        context.setSourceRef(request.getTransactionRef().trim());
        context.setCustomerRef(request.getCustomerRef() != null ? request.getCustomerRef() : request.getCardHash());
        context.setCardHash(request.getCardHash());
        context.setMaskedCard(request.getMaskedCard());
        context.setMerchantId(request.getMerchantId());
        context.setMerchantName(request.getMerchantName());
        context.setMccCode(request.getMccCode());
        context.setAmount(request.getAmount());
        context.setCurrency(request.getCurrency());
        context.setTransactionDate(request.getTransactionDateTime());
        context.setChannel(request.getChannel());
        context.setCampaignId(campaign.getId());
        context.setBenefitType(campaign.getCashbackType() == CashbackType.PERCENTAGE
                ? RewardBenefitType.CASHBACK_PERCENT : RewardBenefitType.CASHBACK_FIXED);
        context.setBenefitValue(campaign.getCashbackValue());
        context.setDetailsJson("{\"campaignCashbackAmount\":" + decision.getCampaignCashbackAmount()
                + ",\"loyaltyBonusEnabled\":" + decision.isLoyaltyBonusEnabled()
                + ",\"loyaltyTierName\":\"" + safe(decision.getLoyaltyTierName())
                + "\",\"loyaltyBonusPercent\":" + decision.getLoyaltyBonusPercent()
                + ",\"loyaltyBonusAmount\":" + decision.getLoyaltyBonusAmount()
                + ",\"finalCashbackAmount\":" + decision.getFinalCashbackAmount() + "}");
        return context;
    }

    private String safe(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\\"]", " "); }

    private Campaign findBestCampaign(CashbackTransactionRequest request) {
        if (request.getMerchantId() == null) return null;
        List<Campaign> campaigns = campaignRepository.findByMerchantId(request.getMerchantId());
        return campaigns.stream()
                .filter(c -> c.getStatus() == CampaignStatus.ACTIVE)
                .filter(c -> c.getTriggerType() == CampaignTriggerType.MERCHANT_TRANSACTION
                        || c.getTriggerType() == CampaignTriggerType.POS_WEBHOOK_EVENT
                        || c.getTriggerType() == CampaignTriggerType.VOLUME_THRESHOLD)
                .sorted(Comparator.comparing(Campaign::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElse(null);
    }
}
