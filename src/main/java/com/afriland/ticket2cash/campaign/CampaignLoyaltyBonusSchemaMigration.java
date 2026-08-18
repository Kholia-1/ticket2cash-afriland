package com.afriland.ticket2cash.campaign;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds the campaign opt-in without changing existing campaign values. */
@Component
@Order(Integer.MIN_VALUE + 20)
public class CampaignLoyaltyBonusSchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public CampaignLoyaltyBonusSchemaMigration(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS loyalty_bonus_enabled BOOLEAN DEFAULT FALSE");
        jdbcTemplate.update("UPDATE campaigns SET loyalty_bonus_enabled = FALSE WHERE loyalty_bonus_enabled IS NULL");
    }
}
