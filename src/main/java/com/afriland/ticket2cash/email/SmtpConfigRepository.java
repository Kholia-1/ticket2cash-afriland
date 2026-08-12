package com.afriland.ticket2cash.email;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, Long> {
}
