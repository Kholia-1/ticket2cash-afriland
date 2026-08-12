package com.afriland.ticket2cash.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PartnerRequestRepository extends JpaRepository<PartnerRequest, Long> {

    /** All requests from one merchant, newest first (used by the partner outbox). */
    List<PartnerRequest> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    /** All requests with a given status, newest first (used by admin inbox filters). */
    List<PartnerRequest> findByStatusOrderByCreatedAtDesc(PartnerRequestStatus status);

    /** All requests, newest first (used by admin inbox). */
    List<PartnerRequest> findAllByOrderByCreatedAtDesc();

    /** Count OPEN + IN_PROGRESS requests — used for admin nav badge. */
    @Query("SELECT COUNT(r) FROM PartnerRequest r WHERE r.status = 'OPEN' OR r.status = 'IN_PROGRESS'")
    long countPending();
}
