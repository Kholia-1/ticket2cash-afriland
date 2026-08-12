package com.afriland.ticket2cash.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyBatchRepository extends JpaRepository<LoyaltyBatch, Long> {
    List<LoyaltyBatch> findAllByOrderByCreatedAtDesc();
    long countByStatus(LoyaltyBatchStatus status);
}
