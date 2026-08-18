package com.afriland.ticket2cash.rewards;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RewardLedgerRepository extends JpaRepository<RewardLedgerEntry, Long> {
    List<RewardLedgerEntry> findBySourceTypeAndSourceRefAndBenefitType(
            RewardSourceType sourceType, String sourceRef, RewardBenefitType benefitType);

    List<RewardLedgerEntry> findTop100ByOrderByCalculatedAtDesc();
}
