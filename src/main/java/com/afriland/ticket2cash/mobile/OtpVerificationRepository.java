package com.afriland.ticket2cash.mobile;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, String> {
}
