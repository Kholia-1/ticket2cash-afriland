package com.afriland.ticket2cash.campaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByMerchantId(Long merchantId);

    List<Campaign> findByStatus(CampaignStatus status);

    // Campaigns whose end date is strictly before the given date (i.e. expired)
    List<Campaign> findByEndDateBefore(LocalDate date);
}
