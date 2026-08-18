package com.afriland.ticket2cash.rewards;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RewardLedgerSchemaMigrationTest {
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void createsLedgerIdempotently() {
        new RewardLedgerSchemaMigration(jdbcTemplate).run();
        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS reward_ledger_entries"));
    }
}
