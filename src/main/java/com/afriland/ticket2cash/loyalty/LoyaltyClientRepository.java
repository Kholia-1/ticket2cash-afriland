package com.afriland.ticket2cash.loyalty;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LoyaltyClientRepository extends JpaRepository<LoyaltyClient, Long> {

    Optional<LoyaltyClient> findByAccountNumber(String accountNumber);

    List<LoyaltyClient> findByTierIgnoreCase(String tier);

    long countByTierIgnoreCase(String tier);

    long countByEntityType(String entityType);

    /** Top clients by all-time volume (either type). */
    @Query("SELECT c FROM LoyaltyClient c ORDER BY c.lifetimeVolume DESC")
    List<LoyaltyClient> findTop(Pageable pageable);

    /** Top clients by all-time volume, filtered by entity type. */
    @Query("SELECT c FROM LoyaltyClient c WHERE UPPER(c.entityType) = UPPER(?1) " +
           "ORDER BY c.lifetimeVolume DESC")
    List<LoyaltyClient> findTopByEntityType(String entityType, Pageable pageable);
}
