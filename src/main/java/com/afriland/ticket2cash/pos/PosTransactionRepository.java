package com.afriland.ticket2cash.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PosTransactionRepository extends JpaRepository<PosTransaction, Long> {

    List<PosTransaction> findByCardHash(String cardHash);

    List<PosTransaction> findByCardHashAndMatchedFalse(String cardHash);

    List<PosTransaction> findByMerchantId(Long merchantId);

    List<PosTransaction> findAllByOrderByReceivedAtDesc();

    List<PosTransaction> findTop100ByOrderByReceivedAtDesc();

    Optional<PosTransaction> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);

    List<PosTransaction> findByMatchedFalse();

    List<PosTransaction> findByMatchedFalseOrderByReceivedAtDesc();

    @Query("select t from PosTransaction t where t.manualReviewRequired = true or t.workflowStatus = :status or t.currentStep = :step order by t.receivedAt desc")
    List<PosTransaction> findReviewQueue(@Param("status") TransactionWorkflowStatus status,
                                          @Param("step") String step);

    @Query("select t from PosTransaction t where t.merchantId = :merchantId and (t.manualReviewRequired = true or t.workflowStatus = :status or t.currentStep = :step) order by t.receivedAt desc")
    List<PosTransaction> findReviewQueueByMerchant(@Param("status") TransactionWorkflowStatus status,
                                                    @Param("step") String step,
                                                    @Param("merchantId") Long merchantId);
}
