package com.afriland.ticket2cash.claim;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimResponseTest {

    @Test
    void publicResponseNeverContainsCardHash() throws Exception {
        Claim claim = new Claim();
        claim.setId(7L);
        claim.setClaimReference("CLM-7");
        claim.setMaskedCard("****1234");
        claim.setCardHash("internal-hash");
        claim.setCashbackAmount(new BigDecimal("500"));

        String json = new ObjectMapper().writeValueAsString(ClaimResponse.from(claim));

        assertTrue(json.contains("maskedCard"));
        assertTrue(json.contains("****1234"));
        assertFalse(json.contains("cardHash"));
        assertFalse(json.contains("internal-hash"));
    }
}
