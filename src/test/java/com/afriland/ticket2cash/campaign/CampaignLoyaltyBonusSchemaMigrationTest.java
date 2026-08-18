package com.afriland.ticket2cash.campaign;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CampaignLoyaltyBonusSchemaMigrationTest {
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void migrationIsAdditiveAndInitializesOnlyNullValues() {
        new CampaignLoyaltyBonusSchemaMigration(jdbcTemplate).run();
        verify(jdbcTemplate).execute("ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS loyalty_bonus_enabled BOOLEAN DEFAULT FALSE");
        verify(jdbcTemplate).update("UPDATE campaigns SET loyalty_bonus_enabled = FALSE WHERE loyalty_bonus_enabled IS NULL");
    }
}
