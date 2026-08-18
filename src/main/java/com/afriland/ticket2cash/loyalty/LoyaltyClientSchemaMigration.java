package com.afriland.ticket2cash.loyalty;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Idempotent migration for the non-reversible loyalty card identifier. */
@Component
@Order(Integer.MIN_VALUE + 30)
public class LoyaltyClientSchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public LoyaltyClientSchemaMigration(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("ALTER TABLE loyalty_clients ADD COLUMN IF NOT EXISTS card_hash VARCHAR(255)");
    }
}
