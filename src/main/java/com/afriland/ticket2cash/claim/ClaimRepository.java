package com.afriland.ticket2cash.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Page<Claim> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    List<Claim> findByMerchantId(Long merchantId);

    List<Claim> findByStatus(ClaimStatus status);

    List<Claim> findByUserId(String userId);

    List<Claim> findByUserIdOrderBySubmittedAtDesc(String userId);

    List<Claim> findByMerchantIdOrderBySubmittedAtDesc(Long merchantId);

    List<Claim> findByCampaignId(Long campaignId);

    List<Claim> findByCardHash(String cardHash);
}
