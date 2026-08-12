package com.afriland.ticket2cash.points;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    List<PointsTransaction> findByAccountNumberOrderByCreatedAtDesc(String accountNumber);
    List<PointsTransaction> findAllByOrderByCreatedAtDesc();

    /** Non-expired EARN transactions past their expiration date, ready to be swept. */
    List<PointsTransaction> findByTypeAndExpiresAtBeforeOrderByExpiresAtAsc(
            PointsTransactionType type, LocalDateTime cutoff);
}
