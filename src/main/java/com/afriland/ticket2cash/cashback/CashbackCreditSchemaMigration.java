package com.afriland.ticket2cash.cashback;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CashbackCreditSchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    public CashbackCreditSchemaMigration(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override public void run(String... args) {
        add("credit_status", "VARCHAR(50)"); add("credit_reference", "VARCHAR(255)");
        add("credited_at", "TIMESTAMP"); add("credit_failure_reason", "VARCHAR(2000)");
        add("prepaid_account_ref", "VARCHAR(255)"); add("customer_ref", "VARCHAR(255)");
        add("campaign_cashback_amount", "DECIMAL(18,2)"); add("loyalty_bonus_enabled", "BOOLEAN");
        add("loyalty_tier_name", "VARCHAR(255)"); add("loyalty_bonus_percent", "DECIMAL(9,4)");
        add("loyalty_bonus_amount", "DECIMAL(18,2)"); add("final_cashback_amount", "DECIMAL(18,2)");
        jdbcTemplate.update("UPDATE cashback_payments SET credit_status = 'CREDIT_PENDING' WHERE credit_status IS NULL");
    }
    private void add(String name, String definition) {
        jdbcTemplate.execute("ALTER TABLE cashback_payments ADD COLUMN IF NOT EXISTS " + name + " " + definition);
    }
}
