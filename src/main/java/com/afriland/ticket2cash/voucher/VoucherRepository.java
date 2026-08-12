package com.afriland.ticket2cash.voucher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /** Vouchers owned by a specific customer, newest first. */
    List<Voucher> findByOwnerAccountNumberOrderByCreatedAtDesc(String ownerAccountNumber);

    /** All vouchers with a given status, newest first. */
    List<Voucher> findByStatusOrderByCreatedAtDesc(VoucherStatus status);

    /** All vouchers, newest first. */
    List<Voucher> findAllByOrderByCreatedAtDesc();

    boolean existsByCode(String code);

    /** For merchant-scoped queries. */
    List<Voucher> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    @Query("SELECT COUNT(v) FROM Voucher v WHERE v.status = 'ISSUED'")
    long countIssued();

    @Query("SELECT COUNT(v) FROM Voucher v WHERE v.status = 'CONSUMED'")
    long countConsumed();
}
