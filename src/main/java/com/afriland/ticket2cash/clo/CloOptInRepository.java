package com.afriland.ticket2cash.clo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CloOptInRepository extends JpaRepository<CloOptIn, Long> {
    Optional<CloOptIn> findByAccountNumber(String accountNumber);
    List<CloOptIn> findByOptedInTrueOrderByOptedInAtDesc();

    long countByOptedInTrue();
}
