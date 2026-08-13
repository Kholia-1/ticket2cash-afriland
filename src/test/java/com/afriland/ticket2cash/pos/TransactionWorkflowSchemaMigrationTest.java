package com.afriland.ticket2cash.pos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionWorkflowSchemaMigrationTest {
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void migrationIsIdempotentAndInitializesOnlyMissingValues() {
        new TransactionWorkflowSchemaMigration(jdbcTemplate).run();

        verify(jdbcTemplate).execute("ALTER TABLE pos_transactions ADD COLUMN IF NOT EXISTS workflow_status VARCHAR(50)");
        verify(jdbcTemplate).execute("ALTER TABLE pos_transactions ADD COLUMN IF NOT EXISTS current_step VARCHAR(50)");
        verify(jdbcTemplate).execute("ALTER TABLE pos_transactions ADD COLUMN IF NOT EXISTS manual_review_required BOOLEAN DEFAULT FALSE");
        verify(jdbcTemplate).execute("ALTER TABLE pos_transactions ADD COLUMN IF NOT EXISTS validated_by VARCHAR(255)");
        verify(jdbcTemplate).execute("ALTER TABLE pos_transactions ADD COLUMN IF NOT EXISTS validated_at TIMESTAMP");
        verify(jdbcTemplate).execute("ALTER TABLE pos_transactions ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(2000)");
        verify(jdbcTemplate).execute("ALTER TABLE pos_transactions ADD COLUMN IF NOT EXISTS last_workflow_comment VARCHAR(2000)");
        verify(jdbcTemplate).update("UPDATE pos_transactions SET workflow_status = 'RECEIVED' WHERE workflow_status IS NULL");
        verify(jdbcTemplate).update("UPDATE pos_transactions SET current_step = 'RECEIVED' WHERE current_step IS NULL");
        verify(jdbcTemplate).update("UPDATE pos_transactions SET manual_review_required = FALSE WHERE manual_review_required IS NULL");
    }
}
