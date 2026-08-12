package com.afriland.ticket2cash.cashback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CashbackPaymentRepository extends JpaRepository<CashbackPayment, Long> {

    Page<CashbackPayment> findAllByOrderByIdDesc(Pageable pageable);

    List<CashbackPayment> findByUserId(String userId);

    List<CashbackPayment> findByMerchantId(Long merchantId);
}
