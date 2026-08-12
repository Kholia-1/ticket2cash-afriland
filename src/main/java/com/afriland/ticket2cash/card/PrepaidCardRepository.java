package com.afriland.ticket2cash.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PrepaidCardRepository extends JpaRepository<PrepaidCard, Long> {

    List<PrepaidCard> findByOwnerAccountNumberOrderByCreatedAtDesc(String ownerAccountNumber);
    List<PrepaidCard> findByBinOrderByCreatedAtDesc(String bin);
    List<PrepaidCard> findByStatusOrderByCreatedAtDesc(PrepaidCardStatus status);
    List<PrepaidCard> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(c) FROM PrepaidCard c WHERE c.status = 'ACTIVE'")
    long countActive();

    @Query("SELECT c.bin, COUNT(c) FROM PrepaidCard c GROUP BY c.bin")
    List<Object[]> countByBin();
}
