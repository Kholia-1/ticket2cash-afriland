package com.afriland.ticket2cash.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findByBatchId(Long batchId);

    List<LoyaltyTransaction> findByBatchIdAndAccountNumber(Long batchId, String accountNumber);

    long countByBatchId(Long batchId);

    boolean existsByReferenceNumberAndAccountNumber(String referenceNumber, String accountNumber);

    void deleteByBatchId(Long batchId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM LoyaltyTransaction t " +
           "WHERE t.batchId = :batchId AND t.accountNumber = :account AND t.qualified = true")
    BigDecimal sumQualifiedAmount(@Param("batchId") Long batchId, @Param("account") String account);
}
