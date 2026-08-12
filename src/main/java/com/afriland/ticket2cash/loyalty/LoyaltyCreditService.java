package com.afriland.ticket2cash.loyalty;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Handles the approval → credit lifecycle of loyalty results.
 *
 * <p>The credit step is intentionally a two-phase commit against Core Banking:
 * approval flips results to QUEUED; the actual credit is performed row-by-row
 * and each row is marked CREDITED (with a Core Banking reference) or FAILED
 * (with an error note). This keeps a partial run recoverable.
 *
 * <p>Currently the {@code creditToCoreBanking} step is a stub that just
 * generates a mock reference — it should be replaced with a real call to the
 * Afriland Core Banking API when integration credentials are available. The
 * batch state machine, reversal, and audit trail all work against that stub
 * as-is.
 */
@Service
public class LoyaltyCreditService {

    private final LoyaltyBatchRepository batchRepository;
    private final LoyaltyResultRepository resultRepository;
    private final LoyaltyClientRepository clientRepository;

    public LoyaltyCreditService(LoyaltyBatchRepository batchRepository,
                                LoyaltyResultRepository resultRepository,
                                LoyaltyClientRepository clientRepository) {
        this.batchRepository = batchRepository;
        this.resultRepository = resultRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public LoyaltyBatch approve(Long batchId, String approver) {
        LoyaltyBatch batch = batchRepository.findById(batchId).orElseThrow();
        if (batch.getStatus() != LoyaltyBatchStatus.CALCULATED) {
            throw new IllegalStateException("Only CALCULATED batches can be approved.");
        }
        batch.setStatus(LoyaltyBatchStatus.APPROVED);
        batch.setApprovedAt(LocalDateTime.now());
        batch.setApprovedBy(approver);

        List<LoyaltyResult> pending = resultRepository.findByBatchIdAndStatus(batchId, LoyaltyResultStatus.PENDING);
        for (LoyaltyResult r : pending) {
            if (r.getCashbackAmount().signum() > 0) {
                r.setStatus(LoyaltyResultStatus.QUEUED);
            } else {
                // Zero cashback — nothing to pay; keep as PENDING for reporting or cancel
                r.setStatus(LoyaltyResultStatus.CANCELLED);
                r.setNote(r.getNote() == null ? "Zero cashback" : r.getNote());
            }
        }
        resultRepository.saveAll(pending);
        return batchRepository.save(batch);
    }

    @Transactional
    public LoyaltyBatch reject(Long batchId, String reason) {
        LoyaltyBatch batch = batchRepository.findById(batchId).orElseThrow();
        if (batch.getStatus() == LoyaltyBatchStatus.CREDITED) {
            throw new IllegalStateException("Cannot reject a batch that has been credited.");
        }
        batch.setStatus(LoyaltyBatchStatus.REJECTED);
        batch.setNote(reason == null ? "Rejected" : reason);
        List<LoyaltyResult> pending = resultRepository.findByBatchId(batchId);
        for (LoyaltyResult r : pending) {
            if (r.getStatus() != LoyaltyResultStatus.CREDITED) {
                r.setStatus(LoyaltyResultStatus.CANCELLED);
            }
        }
        resultRepository.saveAll(pending);
        return batchRepository.save(batch);
    }

    @Transactional
    public LoyaltyBatch credit(Long batchId) {
        LoyaltyBatch batch = batchRepository.findById(batchId).orElseThrow();
        if (batch.getStatus() != LoyaltyBatchStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED batches can be credited.");
        }
        List<LoyaltyResult> queue = resultRepository.findByBatchIdAndStatus(batchId, LoyaltyResultStatus.QUEUED);
        int ok = 0, ko = 0;
        for (LoyaltyResult r : queue) {
            try {
                String ref = creditToCoreBanking(r);
                r.setCreditReference(ref);
                r.setCreditedAt(LocalDateTime.now());
                r.setStatus(LoyaltyResultStatus.CREDITED);
                // Update client aggregates only after successful credit
                clientRepository.findByAccountNumber(r.getAccountNumber()).ifPresent(c -> {
                    BigDecimal newLifetime = (c.getLifetimeCashback() == null ? BigDecimal.ZERO : c.getLifetimeCashback())
                            .add(r.getCashbackAmount());
                    BigDecimal newVolume = (c.getLifetimeVolume() == null ? BigDecimal.ZERO : c.getLifetimeVolume())
                            .add(r.getTotalVolume());
                    c.setLifetimeCashback(newLifetime);
                    c.setLifetimeVolume(newVolume);
                    c.setLastActivityAt(LocalDateTime.now());
                    clientRepository.save(c);
                });
                ok++;
            } catch (Exception e) {
                r.setStatus(LoyaltyResultStatus.FAILED);
                r.setNote("Credit failed: " + e.getMessage());
                ko++;
            }
        }
        resultRepository.saveAll(queue);

        batch.setStatus(LoyaltyBatchStatus.CREDITED);
        batch.setCreditedAt(LocalDateTime.now());
        batch.setNote(String.format("Credited %d succeeded, %d failed.", ok, ko));
        return batchRepository.save(batch);
    }

    /**
     * TODO: Replace with a real Core Banking POST when the Afriland API is
     * available. Signature is intentionally minimal so the swap is a one-liner.
     *
     * @return the Core Banking transaction reference for this credit.
     */
    private String creditToCoreBanking(LoyaltyResult result) {
        // Simulated: 5% failure to exercise the FAILED path in demos.
        // Remove this when hooking to the real API.
        // if (Math.random() < 0.05) throw new RuntimeException("Core Banking unavailable");
        return "AFB-LOY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
