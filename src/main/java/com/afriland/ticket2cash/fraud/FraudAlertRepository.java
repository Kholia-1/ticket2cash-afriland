package com.afriland.ticket2cash.fraud;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    Page<FraudAlert> findAllByOrderByIdDesc(Pageable pageable);

    List<FraudAlert> findByStatus(FraudAlertStatus status);

    List<FraudAlert> findByMerchantId(Long merchantId);
}
