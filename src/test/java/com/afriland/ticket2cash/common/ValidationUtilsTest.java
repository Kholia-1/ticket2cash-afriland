package com.afriland.ticket2cash.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ValidationUtilsTest {

    @Test
    void acceptsValidCardAndRejectsInvalidCard() {
        assertNull(ValidationUtils.validateCardNumber("4532 1234 5678 9012"));
        assertNotNull(ValidationUtils.validateCardNumber("1234"));
    }

    @Test
    void rejectsNegativeAmounts() {
        assertNotNull(ValidationUtils.validatePositive(new BigDecimal("-1"), "montant"));
        assertNull(ValidationUtils.validatePositive(new BigDecimal("10"), "montant"));
    }

    @Test
    void acceptsInternationalPhoneAndRejectsInvalidPhone() {
        assertNull(ValidationUtils.validatePhone("+237 699 00 11 22"));
        assertNotNull(ValidationUtils.validatePhone("abc"));
    }
}
