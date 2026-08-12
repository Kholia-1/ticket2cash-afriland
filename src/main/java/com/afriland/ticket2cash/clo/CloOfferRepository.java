package com.afriland.ticket2cash.clo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CloOfferRepository extends JpaRepository<CloOffer, Long> {
    List<CloOffer> findAllByOrderByCreatedAtDesc();
    List<CloOffer> findByStatusOrderByCreatedAtDesc(CloOfferStatus status);
    List<CloOffer> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    @Query("SELECT COUNT(o) FROM CloOffer o WHERE o.status = 'ACTIVE'")
    long countActive();
}
