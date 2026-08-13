package com.afriland.ticket2cash.pos;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Compatibility migration for databases created before workflow fields were added.
 * It is intentionally idempotent and only fills missing values; existing workflow
 * decisions are never overwritten.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TransactionWorkflowSchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public TransactionWorkflowSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumn("workflow_status", "VARCHAR(50)");
        addColumn("current_step", "VARCHAR(50)");
        addColumn("manual_review_required", "BOOLEAN DEFAULT FALSE");
        addColumn("validated_by", "VARCHAR(255)");
        addColumn("validated_at", "TIMESTAMP");
        addColumn("rejection_reason", "VARCHAR(2000)");
        addColumn("last_workflow_comment", "VARCHAR(2000)");

        jdbcTemplate.update("UPDATE pos_transactions SET workflow_status = 'RECEIVED' WHERE workflow_status IS NULL");
        jdbcTemplate.update("UPDATE pos_transactions SET current_step = 'RECEIVED' WHERE current_step IS NULL");
        jdbcTemplate.update("UPDATE pos_transactions SET manual_review_required = FALSE WHERE manual_review_required IS NULL");
    }

    private void addColumn(String name, String definition) {
        jdbcTemplate.execute("ALTER TABLE pos_transactions ADD COLUMN IF NOT EXISTS " + name + " " + definition);
    }
}
