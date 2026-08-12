package com.afriland.ticket2cash.mobile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MobileClientRepository extends JpaRepository<MobileClient, Long> {

    Optional<MobileClient> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByCardHash(String cardHash);
}
