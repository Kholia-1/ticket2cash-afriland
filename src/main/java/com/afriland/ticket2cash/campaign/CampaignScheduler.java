package com.afriland.ticket2cash.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Automatically removes campaigns whose end date has already passed.
 * Runs about a minute after startup and then every hour.
 * A campaign with endDate == today is still considered active for that day.
 */
@Component
public class CampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(CampaignScheduler.class);

    private final CampaignRepository campaignRepository;

    public CampaignScheduler(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Scheduled(initialDelay = 60000, fixedRate = 3600000)
    public void deleteExpiredCampaigns() {
        LocalDate today = LocalDate.now();
        List<Campaign> expired = campaignRepository.findByEndDateBefore(today);
        if (!expired.isEmpty()) {
            campaignRepository.deleteAll(expired);
            log.info("Auto-deleted {} expired campaign(s)", expired.size());
        }
    }
}
