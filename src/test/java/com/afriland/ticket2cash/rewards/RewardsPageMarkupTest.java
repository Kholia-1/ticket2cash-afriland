package com.afriland.ticket2cash.rewards;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsPageMarkupTest {
    @Test
    void customerBenefitsPageHasSummaryEndpointAndEmptyState() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));
        assertTrue(html.contains("Avantages clients"));
        assertTrue(html.contains("/api/rewards/summary"));
        assertTrue(html.contains("Aucun avantage client enregistré pour le moment."));
    }
}
