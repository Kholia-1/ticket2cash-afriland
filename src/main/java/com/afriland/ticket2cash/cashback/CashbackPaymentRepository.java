package com.afriland.ticket2cash.cashback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashbackPaymentRepository extends JpaRepository<CashbackPayment, Long> {

    List<CashbackPayment> findByUserId(String userId);

    List<CashbackPayment> findByMerchantId(Long merchantId);
}