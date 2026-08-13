package com.afriland.ticket2cash.cashback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CashbackCreditSchemaMigrationTest {
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void migrationAddsCreditColumnsAndPreservesExistingValues() {
        new CashbackCreditSchemaMigration(jdbcTemplate).run();
        verify(jdbcTemplate).execute("ALTER TABLE cashback_payments ADD COLUMN IF NOT EXISTS credit_status VARCHAR(50)");
        verify(jdbcTemplate).execute("ALTER TABLE cashback_payments ADD COLUMN IF NOT EXISTS credit_reference VARCHAR(255)");
        verify(jdbcTemplate).execute("ALTER TABLE cashback_payments ADD COLUMN IF NOT EXISTS credited_at TIMESTAMP");
        verify(jdbcTemplate).execute("ALTER TABLE cashback_payments ADD COLUMN IF NOT EXISTS credit_failure_reason VARCHAR(2000)");
        verify(jdbcTemplate).execute("ALTER TABLE cashback_payments ADD COLUMN IF NOT EXISTS prepaid_account_ref VARCHAR(255)");
        verify(jdbcTemplate).execute("ALTER TABLE cashback_payments ADD COLUMN IF NOT EXISTS customer_ref VARCHAR(255)");
        verify(jdbcTemplate).update("UPDATE cashback_payments SET credit_status = 'CREDIT_PENDING' WHERE credit_status IS NULL");
    }
}
