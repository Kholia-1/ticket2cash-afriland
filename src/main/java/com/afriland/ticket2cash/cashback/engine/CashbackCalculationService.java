package com.afriland.ticket2cash.cashback.engine;

import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.product.CashbackType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CashbackCalculationService {

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

    private CashbackDecision rejected(CashbackDecisionCode code, String message,
                                      BigDecimal ticketAmount,
                                      CashbackCalculationRequest request,
                                      Long campaignId) {
        return CashbackDecision.rejected(code, message, ticketAmount,
                request.getFraudScore(), campaignId,
                request.getMerchantId(), request.getTicketId());
    }
}
