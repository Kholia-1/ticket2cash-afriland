package com.afriland.ticket2cash.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyResultRepository extends JpaRepository<LoyaltyResult, Long> {
    List<LoyaltyResult> findByBatchId(Long batchId);
    List<LoyaltyResult> findByBatchIdOrderByCashbackAmountDesc(Long batchId);
    List<LoyaltyResult> findByBatchIdAndStatus(Long batchId, LoyaltyResultStatus status);
    void deleteByBatchId(Long batchId);
    long countByBatchIdAndStatus(Long batchId, LoyaltyResultStatus status);
}
