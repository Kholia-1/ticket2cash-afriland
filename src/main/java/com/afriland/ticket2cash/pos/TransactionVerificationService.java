package com.afriland.ticket2cash.pos;

import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Verifies scanned receipts against real POS transactions received via webhook.
 * 
 * Matching criteria (scored):
 * - Card hash match: +40 points (same card used)
 * - Merchant match: +25 points (same store)
 * - Amount match (±5%): +25 points (same total)
 * - Date match (same day): +10 points
 * 
 * Score >= 65 = VERIFIED (auto-approve)
 * Score 40-64 = PARTIAL (needs manual review)
 * Score < 40 = UNVERIFIED (flagged)
 */
@Service
public class TransactionVerificationService {

    private final PosTransactionRepository posRepository;
    private final ClaimRepository claimRepository;

    public TransactionVerificationService(PosTransactionRepository posRepository,
                                           ClaimRepository claimRepository) {
        this.posRepository = posRepository;
        this.claimRepository = claimRepository;
    }

    /**
     * Verify a claim against POS transactions.
     * Returns a verification result with score and matched transaction.
     */
    public VerificationResult verify(Claim claim) {
        if (claim.getCardHash() == null || claim.getCardHash().isEmpty()) {
            return new VerificationResult(0, "NO_CARD", "No card linked to this claim", null);
        }

        // Find unmatched POS transactions for this card
        List<PosTransaction> candidates = posRepository.findByCardHashAndMatchedFalse(claim.getCardHash());

        if (candidates.isEmpty()) {
            // Also check matched ones (card might have multiple transactions)
            candidates = posRepository.findByCardHash(claim.getCardHash());
            if (candidates.isEmpty()) {
                return new VerificationResult(0, "NO_POS_DATA",
                    "No POS transaction found for this card", null);
            }
        }

        // Score each candidate
        PosTransaction bestMatch = null;
        int bestScore = 0;
        String bestDetail = "";

        for (PosTransaction pos : candidates) {
            int score = 0;
            StringBuilder detail = new StringBuilder();

            // Card match (+40)
            if (claim.getCardHash().equals(pos.getCardHash())) {
                score += 40;
                detail.append("Card match. ");
            }

            // Merchant match (+25)
            if (claim.getMerchantId() != null && claim.getMerchantId().equals(pos.getMerchantId())) {
                score += 25;
                detail.append("Merchant match. ");
            }

            // Amount match within 5% tolerance (+25)
            if (claim.getTicketAmount() != null && pos.getAmount() != null) {
                BigDecimal diff = claim.getTicketAmount().subtract(pos.getAmount()).abs();
                BigDecimal tolerance = pos.getAmount().multiply(BigDecimal.valueOf(0.05));
                if (diff.compareTo(tolerance) <= 0) {
                    score += 25;
                    detail.append("Amount match (" + claim.getTicketAmount() + " vs " + pos.getAmount() + "). ");
                } else {
                    detail.append("Amount mismatch (" + claim.getTicketAmount() + " vs " + pos.getAmount() + "). ");
                }
            }

            // Date match — same day (+10)
            if (claim.getSubmittedAt() != null && pos.getTransactionDate() != null) {
                if (claim.getSubmittedAt().toLocalDate().equals(pos.getTransactionDate().toLocalDate())) {
                    score += 10;
                    detail.append("Same day. ");
                } else {
                    // Within 3 days still gets some credit
                    long daysDiff = Math.abs(claim.getSubmittedAt().toLocalDate()
                        .toEpochDay() - pos.getTransactionDate().toLocalDate().toEpochDay());
                    if (daysDiff <= 3) {
                        score += 5;
                        detail.append("Within 3 days. ");
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMatch = pos;
                bestDetail = detail.toString();
            }
        }

        // Determine verification status
        String status;
        if (bestScore >= 65) {
            status = "VERIFIED";
            // Mark POS transaction as matched
            if (bestMatch != null && !bestMatch.isMatched()) {
                bestMatch.setMatched(true);
                bestMatch.setMatchedClaimId(claim.getId());
                posRepository.save(bestMatch);
            }
        } else if (bestScore >= 40) {
            status = "PARTIAL";
        } else {
            status = "UNVERIFIED";
        }

        return new VerificationResult(bestScore, status, bestDetail, bestMatch);
    }

    /**
     * Result of a transaction verification.
     */
    public static class VerificationResult {
        public final int score;
        public final String status;       // VERIFIED, PARTIAL, UNVERIFIED, NO_CARD, NO_POS_DATA
        public final String details;
        public final PosTransaction matchedTransaction;

        public VerificationResult(int score, String status, String details,
                                   PosTransaction matchedTransaction) {
            this.score = score;
            this.status = status;
            this.details = details;
            this.matchedTransaction = matchedTransaction;
        }
    }
}
