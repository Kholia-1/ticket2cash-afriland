package com.afriland.ticket2cash.clo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CloRedemptionRepository extends JpaRepository<CloRedemption, Long> {
    List<CloRedemption> findAllByOrderByRedeemedAtDesc();
    List<CloRedemption> findByOfferIdOrderByRedeemedAtDesc(Long offerId);
    List<CloRedemption> findByAccountNumberOrderByRedeemedAtDesc(String accountNumber);
}
