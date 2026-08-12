package com.afriland.ticket2cash.fraud;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    List<FraudAlert> findByStatus(FraudAlertStatus status);

    List<FraudAlert> findByMerchantId(Long merchantId);
}