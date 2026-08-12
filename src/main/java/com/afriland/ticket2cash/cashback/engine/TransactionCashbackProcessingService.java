package com.afriland.ticket2cash.cashback.engine;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.cashback.CashbackPayment;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.cashback.CashbackPaymentStatus;
import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.campaign.CampaignTriggerType;
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

    public TransactionCashbackProcessingService(CampaignRepository campaignRepository,
                                                CashbackCalculationService calculationService,
                                                CashbackPaymentRepository paymentRepository,
                                                AuditLogService auditLogService) {
        this.campaignRepository = campaignRepository;
        this.calculationService = calculationService;
        this.paymentRepository = paymentRepository;
        this.auditLogService = auditLogService;
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
        payment.setUserId(request.getCardHash());
        payment.setAmount(decision.getFinalCashback());
        payment.setCurrency(request.getCurrency() == null || request.getCurrency().isBlank()
                ? "FCFA" : request.getCurrency());
        payment.setCardHash(request.getCardHash());
        payment.setMaskedCard(request.getMaskedCard());
        payment.setCardBin(request.getCardBin());
        payment.setTransactionDateTime(request.getTransactionDateTime());
        payment.setStatus(CashbackPaymentStatus.PENDING);

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException duplicate) {
            return CashbackDecision.rejected(CashbackDecisionCode.REJECTED_DUPLICATE_TICKET,
                    "Transaction has already been processed", request.getAmount(), null,
                    campaign.getId(), request.getMerchantId(), null);
        }

        auditLogService.log("TRANSACTION_CASHBACK_CREATED", "CASHBACK", "CashbackPayment",
                payment.getId(), request.getTransactionRef(), "SUCCESS",
                "Cashback reserved for card transaction");
        return decision;
    }

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
