package com.afriland.ticket2cash.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PosTransactionRepository extends JpaRepository<PosTransaction, Long> {

    List<PosTransaction> findByCardHash(String cardHash);

    List<PosTransaction> findByCardHashAndMatchedFalse(String cardHash);

    List<PosTransaction> findByMerchantId(Long merchantId);

    Optional<PosTransaction> findByTransactionRef(String transactionRef);

    List<PosTransaction> findByMatchedFalse();
}
