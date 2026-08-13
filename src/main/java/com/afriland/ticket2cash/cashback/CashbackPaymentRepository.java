package com.afriland.ticket2cash.cashback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CashbackPaymentRepository extends JpaRepository<CashbackPayment, Long> {

    Page<CashbackPayment> findAllByOrderByIdDesc(Pageable pageable);

    List<CashbackPayment> findByStatusOrderByIdAsc(CashbackPaymentStatus status);

    List<CashbackPayment> findByUserId(String userId);

    List<CashbackPayment> findByMerchantId(Long merchantId);

    Optional<CashbackPayment> findByTransactionRef(String transactionRef);

    List<CashbackPayment> findByCampaignId(Long campaignId);

    List<CashbackPayment> findByCampaignIdAndCardHash(Long campaignId, String cardHash);

    List<CashbackPayment> findByCreditStatusOrderByIdAsc(CashbackCreditStatus creditStatus);
}
