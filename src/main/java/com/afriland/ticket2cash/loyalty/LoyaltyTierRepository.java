package com.afriland.ticket2cash.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, Long> {
    List<LoyaltyTier> findAllByOrderBySortOrderAsc();
    List<LoyaltyTier> findByActiveTrueOrderBySortOrderAsc();
    boolean existsByNameIgnoreCase(String name);
}
