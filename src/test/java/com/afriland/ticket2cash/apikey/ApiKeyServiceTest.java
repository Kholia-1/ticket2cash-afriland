package com.afriland.ticket2cash.apikey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository repository;

    @Test
    void missingApiKeyIsRejected() {
        ApiKeyService service = new ApiKeyService(repository);

        assertTrue(service.authenticate(null).isEmpty());
    }

    @Test
    void validApiKeyUpdatesUsageAndReturnsKey() {
        ApiKeyService service = new ApiKeyService(repository);
        ApiKey apiKey = new ApiKey();
        apiKey.setActive(true);
        apiKey.setCallCount(2L);
        String rawKey = "test-api-key";
        when(repository.findByKeyHash(service.hash(rawKey))).thenReturn(Optional.of(apiKey));

        Optional<ApiKey> authenticated = service.authenticate(rawKey);

        assertSame(apiKey, authenticated.orElseThrow());
        assertEquals(3L, apiKey.getCallCount());
        verify(repository).save(apiKey);
    }
}
