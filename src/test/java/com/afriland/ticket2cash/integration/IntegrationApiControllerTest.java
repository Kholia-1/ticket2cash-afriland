package com.afriland.ticket2cash.integration;

import com.afriland.ticket2cash.apikey.ApiKey;
import com.afriland.ticket2cash.apikey.ApiKeyService;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.pos.PosTransactionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationApiControllerTest {

    @Mock ApiKeyService apiKeyService;
    @Mock PosTransactionRepository posRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock HttpServletRequest request;

    @Test
    void batchRejectsMoreThanThousandItems() {
        ApiKey key = new ApiKey();
        key.setActive(true);
        when(request.getHeader("X-API-Key")).thenReturn("test-key");
        when(apiKeyService.authenticate("test-key")).thenReturn(Optional.of(key));

        IntegrationApiController controller = new IntegrationApiController(
                apiKeyService, posRepository, merchantRepository);
        ResponseEntity<?> response = controller.pushBatch(
                new ArrayList<>(java.util.Collections.nCopies(1001, Map.of())), request);

        assertEquals(400, response.getStatusCode().value());
    }
}
