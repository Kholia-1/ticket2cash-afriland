package com.afriland.ticket2cash.points;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PointsAccountRepository extends JpaRepository<PointsAccount, Long> {
    Optional<PointsAccount> findByAccountNumber(String accountNumber);
    List<PointsAccount> findAllByOrderByBalanceDesc();

    @Query("SELECT SUM(a.balance) FROM PointsAccount a")
    Long sumAllBalances();

    @Query("SELECT SUM(a.totalEarned) FROM PointsAccount a")
    Long sumAllEarned();

    @Query("SELECT SUM(a.totalBurned) FROM PointsAccount a")
    Long sumAllBurned();
}
