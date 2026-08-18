package com.afriland.ticket2cash.cashback.engine;

import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.product.CashbackType;
import com.afriland.ticket2cash.cashback.CashbackPaymentRepository;
import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.loyalty.LoyaltyBonusResult;
import com.afriland.ticket2cash.loyalty.LoyaltyBonusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CashbackCalculationService {

    private final CashbackPaymentRepository paymentRepository;
    private final LoyaltyBonusService loyaltyBonusService;
    private final AuditLogService auditLogService;

    public CashbackCalculationService() {
        this(null, null, null);
    }

    public CashbackCalculationService(CashbackPaymentRepository paymentRepository) {
        this(paymentRepository, null, null);
    }

    @Autowired
    public CashbackCalculationService(CashbackPaymentRepository paymentRepository,
                                      LoyaltyBonusService loyaltyBonusService,
                                      AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository;
        this.loyaltyBonusService = loyaltyBonusService;
        this.auditLogService = auditLogService;
    }

    public CashbackDecision calculate(Campaign campaign, CashbackCalculationRequest request) {
        if (request == null || request.getTicketAmount() == null
                || request.getTicketAmount().signum() <= 0) {
            return CashbackDecision.rejected(
                    CashbackDecisionCode.REJECTED_INVALID_AMOUNT,
                    "Ticket amount must be greater than zero",
                    request == null ? null : request.getTicketAmount(),
                    request == null ? null : request.getFraudScore(),
                    campaign == null ? null : campaign.getId(),
                    request == null ? null : request.getMerchantId(),
                    request == null ? null : request.getTicketId());
        }

        BigDecimal ticketAmount = request.getTicketAmount();
        if (campaign == null) {
            return rejected(CashbackDecisionCode.REJECTED_CAMPAIGN_NULL,
                    "Campaign is required", ticketAmount, request, null);
        }

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            return rejected(CashbackDecisionCode.REJECTED_CAMPAIGN_INACTIVE,
                    "Campaign is not active", ticketAmount, request, campaign.getId());
        }

        LocalDate transactionDate = (request.getTransactionDateTime() == null
                ? LocalDate.now() : request.getTransactionDateTime().toLocalDate());
        if ((campaign.getStartDate() != null && transactionDate.isBefore(campaign.getStartDate()))
                || (campaign.getEndDate() != null && transactionDate.isAfter(campaign.getEndDate()))) {
            return rejected(CashbackDecisionCode.REJECTED_CAMPAIGN_OUT_OF_PERIOD,
                    "Transaction is outside the campaign period", ticketAmount, request, campaign.getId());
        }

        if (campaign.getMinTransactionAmount() != null
                && ticketAmount.compareTo(campaign.getMinTransactionAmount()) < 0) {
            return rejected(CashbackDecisionCode.REJECTED_MIN_AMOUNT_NOT_REACHED,
                    "Ticket amount is below the campaign minimum", ticketAmount, request, campaign.getId());
        }

        CashbackType cashbackType = campaign.getCashbackType();
        BigDecimal cashbackValue = campaign.getCashbackValue();
        if (cashbackType == null || cashbackValue == null || cashbackType == CashbackType.NONE) {
            return rejected(CashbackDecisionCode.REJECTED_NO_CASHBACK_RULE,
                    "Campaign has no cashback rule", ticketAmount, request, campaign.getId());
        }

        if (request.getFraudScore() != null && request.getFraudScore() >= 80) {
            return rejected(CashbackDecisionCode.REJECTED_FRAUD_SUSPECTED,
                    "Fraud score is too high", ticketAmount, request, campaign.getId());
        }

        BigDecimal cashback;
        if (cashbackType == CashbackType.PERCENTAGE) {
            cashback = ticketAmount.multiply(cashbackValue)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        } else if (cashbackType == CashbackType.FIXED_AMOUNT) {
            cashback = cashbackValue;
        } else {
            return rejected(CashbackDecisionCode.REJECTED_NO_CASHBACK_RULE,
                    "Campaign has no supported cashback rule", ticketAmount, request, campaign.getId());
        }

        return CashbackDecision.approved(
                CashbackDecisionCode.APPROVED,
                "Cashback approved",
                ticketAmount,
                cashback,
                request.getFraudScore(),
                campaign.getId(),
                request.getMerchantId(),
                request.getTicketId());
    }

    public CashbackDecision calculateFromTransaction(Campaign campaign,
                                                     CashbackTransactionRequest request) {
        if (request == null || request.getTransactionRef() == null
                || request.getTransactionRef().isBlank()) {
            return transactionRejected(CashbackDecisionCode.REJECTED_TRANSACTION_REF_REQUIRED,
                    "Transaction reference is required", request, campaign);
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            return transactionRejected(CashbackDecisionCode.REJECTED_INVALID_AMOUNT,
                    "Transaction amount must be greater than zero", request, campaign);
        }
        if (request.getCardHash() == null || request.getCardHash().isBlank()) {
            return transactionRejected(CashbackDecisionCode.REJECTED_CARD_HASH_REQUIRED,
                    "Card hash is required", request, campaign);
        }
        if (paymentRepository != null && paymentRepository.findByTransactionRef(request.getTransactionRef().trim()).isPresent()) {
            return transactionRejected(CashbackDecisionCode.REJECTED_DUPLICATE_TICKET,
                    "Transaction has already been processed", request, campaign);
        }

        if (campaign == null) {
            return transactionRejected(CashbackDecisionCode.REJECTED_CAMPAIGN_NULL,
                    "Campaign is required", request, campaign);
        }

        if (campaign.getMccCode() != null && !matchesMcc(campaign.getMccCode().getCode(), request.getMccCode())) {
            return transactionRejected(CashbackDecisionCode.REJECTED_MCC_NOT_ELIGIBLE,
                    "Transaction MCC does not match the campaign", request, campaign);
        }
        if (campaign.getChannelFilter() != null
                && campaign.getChannelFilter().name() != null
                && !"ALL".equalsIgnoreCase(campaign.getChannelFilter().name())
                && !equalsIgnoreCase(campaign.getChannelFilter().name(), request.getChannel())) {
            return transactionRejected(CashbackDecisionCode.REJECTED_CHANNEL_NOT_ELIGIBLE,
                    "Transaction channel does not match the campaign", request, campaign);
        }
        if (!matchesCardBin(campaign.getCardBinStart(), campaign.getCardBinEnd(), request.getCardBin())) {
            return transactionRejected(CashbackDecisionCode.REJECTED_CARD_BIN_NOT_ELIGIBLE,
                    "Transaction card BIN does not match the campaign", request, campaign);
        }

        CashbackCalculationRequest calculationRequest = new CashbackCalculationRequest();
        calculationRequest.setTicketAmount(request.getAmount());
        calculationRequest.setCardHash(request.getCardHash());
        calculationRequest.setFraudScore(null);
        calculationRequest.setTransactionDateTime(request.getTransactionDateTime());
        calculationRequest.setMerchantId(request.getMerchantId());
        calculationRequest.setCampaignId(campaign.getId());
        CashbackDecision decision = calculate(campaign, calculationRequest);
        if (!decision.isEligible()) return decision;

        BigDecimal campaignCashback = decision.getCalculatedCashback();
        decision.setCampaignCashbackAmount(campaignCashback);
        BigDecimal loyaltyBonus = BigDecimal.ZERO;
        if (loyaltyBonusService != null) {
            LoyaltyBonusResult bonus = loyaltyBonusService.resolve(campaign,
                    request.getCustomerRef(), request.getCardHash(), request.getAmount(),
                    request.getTransactionRef(), request.getMaskedCard());
            decision.setLoyaltyBonusEnabled(Boolean.TRUE.equals(campaign.getLoyaltyBonusEnabled()));
            decision.setLoyaltyTierName(bonus.getTierName());
            decision.setLoyaltyBonusPercent(bonus.getBonusPercent());
            loyaltyBonus = bonus.getBonusAmount() == null ? BigDecimal.ZERO : bonus.getBonusAmount();
            decision.setLoyaltyBonusAmount(loyaltyBonus);
            if (bonus.getDecisionCode() == com.afriland.ticket2cash.loyalty.LoyaltyBonusDecisionCode.APPLIED) {
                auditLoyaltyBonus(request, campaign, bonus);
            }
        }
        BigDecimal finalCashback = campaignCashback.add(loyaltyBonus);
        BigDecimal budgetRemaining = remainingBudget(campaign);
        if (campaign.getTotalBudget() != null) {
            decision.setCampaignBudgetRemaining(budgetRemaining.max(BigDecimal.ZERO));
            if (budgetRemaining.signum() <= 0) {
                return transactionRejected(CashbackDecisionCode.REJECTED_BUDGET_EXCEEDED,
                        "Campaign budget is exhausted", request, campaign);
            }
            if (finalCashback.compareTo(budgetRemaining) > 0) {
                finalCashback = budgetRemaining;
                decision.setCode(CashbackDecisionCode.APPROVED_WITH_LIMIT);
            }
        }

        BigDecimal clientCommitted = committedForCard(campaign, request.getCardHash());
        BigDecimal dailyRemaining = remainingLimit(campaign.getDailyLimitPerClient(),
                committedForCardOnDate(campaign, request.getCardHash(), request.getTransactionDateTime(), true));
        BigDecimal monthlyRemaining = remainingLimit(campaign.getMonthlyLimitPerClient(),
                committedForCardOnDate(campaign, request.getCardHash(), request.getTransactionDateTime(), false));
        if (dailyRemaining != null && finalCashback.compareTo(dailyRemaining) > 0) {
            finalCashback = dailyRemaining.max(BigDecimal.ZERO);
            decision.setCode(CashbackDecisionCode.APPROVED_WITH_LIMIT);
        }
        if (monthlyRemaining != null && finalCashback.compareTo(monthlyRemaining) > 0) {
            finalCashback = monthlyRemaining.max(BigDecimal.ZERO);
            decision.setCode(CashbackDecisionCode.APPROVED_WITH_LIMIT);
        }
        if (campaign.getMaxCashbackPerClient() != null) {
            BigDecimal clientRemaining = campaign.getMaxCashbackPerClient().subtract(clientCommitted);
            if (clientRemaining.signum() <= 0) {
                return transactionRejected(CashbackDecisionCode.REJECTED_CLIENT_CAP_REACHED,
                        "Client cashback cap is exhausted", request, campaign);
            }
            if (finalCashback.compareTo(clientRemaining) > 0) {
                finalCashback = clientRemaining;
                decision.setCode(CashbackDecisionCode.APPROVED_WITH_LIMIT);
            }
        }
        decision.setFinalCashback(finalCashback.max(BigDecimal.ZERO));
        decision.setDailyRemaining(dailyRemaining);
        decision.setMonthlyRemaining(monthlyRemaining);
        auditCombinedDecision(request, campaign, decision, decision.getFinalCashback());
        return decision;
    }

    private void auditLoyaltyBonus(CashbackTransactionRequest request, Campaign campaign,
                                   LoyaltyBonusResult bonus) {
        if (auditLogService == null) return;
        try {
            auditLogService.log("LOYALTY_BONUS_APPLIED", "CASHBACK", "Campaign",
                    campaign.getId(), request.getTransactionRef(), "SUCCESS",
                    "transactionRef=" + safe(request.getTransactionRef())
                            + " | maskedCard=" + safe(request.getMaskedCard())
                            + " | customerRef=" + safe(request.getCustomerRef())
                            + " | campaignName=" + safe(campaign.getName())
                            + " | tierName=" + safe(bonus.getTierName())
                            + " | loyaltyBonusPercent=" + bonus.getBonusPercent()
                            + " | loyaltyBonusAmount=" + bonus.getBonusAmount());
        } catch (RuntimeException ignored) { }
    }

    private void auditCombinedDecision(CashbackTransactionRequest request, Campaign campaign,
                                       CashbackDecision decision, BigDecimal finalCashback) {
        if (auditLogService == null) return;
        try {
            auditLogService.log("CASHBACK_WITH_LOYALTY_CONTEXT_CALCULATED", "CASHBACK", "Campaign",
                    campaign.getId(), request.getTransactionRef(), "SUCCESS",
                    "transactionRef=" + safe(request.getTransactionRef())
                            + " | maskedCard=" + safe(request.getMaskedCard())
                            + " | customerRef=" + safe(request.getCustomerRef())
                            + " | campaignName=" + safe(campaign.getName())
                            + " | loyaltyBonusEnabled=" + decision.isLoyaltyBonusEnabled()
                            + " | loyaltyBonusPercent=" + decision.getLoyaltyBonusPercent()
                            + " | campaignCashbackAmount=" + decision.getCampaignCashbackAmount()
                            + " | loyaltyBonusAmount=" + decision.getLoyaltyBonusAmount()
                            + " | finalCashbackAmount=" + finalCashback
                            + " | tier=" + safe(decision.getLoyaltyTierName()));
        } catch (RuntimeException ignored) { }
    }

    private String safe(String value) { return value == null ? "" : value.replaceAll("[\\r\\n]", " "); }

    private BigDecimal remainingBudget(Campaign campaign) {
        if (campaign.getTotalBudget() == null || paymentRepository == null) return BigDecimal.ZERO;
        return campaign.getTotalBudget().subtract(paymentRepository.findByCampaignId(campaign.getId()).stream()
                .filter(this::isCommitted)
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal committedForCard(Campaign campaign, String cardHash) {
        if (paymentRepository == null || cardHash == null) return BigDecimal.ZERO;
        return paymentRepository.findByCampaignIdAndCardHash(campaign.getId(), cardHash).stream()
                .filter(this::isCommitted)
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal committedForCardOnDate(Campaign campaign, String cardHash,
                                               LocalDateTime transactionDateTime, boolean day) {
        if (paymentRepository == null || cardHash == null) return BigDecimal.ZERO;
        LocalDate date = (transactionDateTime == null ? LocalDate.now() : transactionDateTime.toLocalDate());
        return paymentRepository.findByCampaignIdAndCardHash(campaign.getId(), cardHash).stream()
                .filter(this::isCommitted)
                .filter(p -> p.getTransactionDateTime() != null)
                .filter(p -> day ? p.getTransactionDateTime().toLocalDate().equals(date)
                        : p.getTransactionDateTime().getYear() == date.getYear()
                        && p.getTransactionDateTime().getMonthValue() == date.getMonthValue())
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal remainingLimit(BigDecimal limit, BigDecimal used) {
        return limit == null ? null : limit.subtract(used).max(BigDecimal.ZERO);
    }

    private boolean isCommitted(com.afriland.ticket2cash.cashback.CashbackPayment payment) {
        return payment.getStatus() != com.afriland.ticket2cash.cashback.CashbackPaymentStatus.FAILED;
    }

    private CashbackDecision transactionRejected(CashbackDecisionCode code, String message,
                                                 CashbackTransactionRequest request,
                                                 Campaign campaign) {
        CashbackDecision decision = CashbackDecision.rejected(code, message,
                request == null ? null : request.getAmount(), null,
                campaign == null ? null : campaign.getId(),
                request == null ? null : request.getMerchantId(), null);
        return decision;
    }

    private boolean matchesMcc(String configured, String actual) {
        if (actual == null || actual.isBlank()) return false;
        return configured.equalsIgnoreCase(actual.trim())
                || configured.replace("MCC_", "").equalsIgnoreCase(actual.trim());
    }

    private boolean matchesCardBin(String start, String end, String actual) {
        if ((start == null || start.isBlank()) && (end == null || end.isBlank())) return true;
        if (actual == null || actual.isBlank()) return false;
        try {
            long value = Long.parseLong(actual.trim());
            long min = start == null || start.isBlank() ? Long.MIN_VALUE : Long.parseLong(start.trim());
            long max = end == null || end.isBlank() ? Long.MAX_VALUE : Long.parseLong(end.trim());
            return value >= min && value <= max;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return right != null && left.equalsIgnoreCase(right.trim());
    }

    private CashbackDecision rejected(CashbackDecisionCode code, String message,
                                      BigDecimal ticketAmount,
                                      CashbackCalculationRequest request,
                                      Long campaignId) {
        return CashbackDecision.rejected(code, message, ticketAmount,
                request.getFraudScore(), campaignId,
                request.getMerchantId(), request.getTicketId());
    }
}
