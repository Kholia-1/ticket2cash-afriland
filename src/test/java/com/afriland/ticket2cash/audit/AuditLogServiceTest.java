package com.afriland.ticket2cash.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository repository;

    @Test
    void sensitiveValuesAreRedactedFromAuditMessage() {
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuditLogService service = new AuditLogService(repository);

        AuditLog log = service.log("TEST", "AUTH", "User", 1L,
                "user", "FAILED", "password=secret pin=1234 apiKey=t2c_secret");

        assertTrue(log.getMessage().contains("[REDACTED]"));
        assertFalse(log.getMessage().contains("secret"));
        assertFalse(log.getMessage().contains("1234"));
    }
}
