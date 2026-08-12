package com.afriland.ticket2cash.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByMerchantId(Long merchantId);

    List<Claim> findByStatus(ClaimStatus status);

    List<Claim> findByUserId(String userId);

    List<Claim> findByCampaignId(Long campaignId);

    List<Claim> findByCardHash(String cardHash);
}
