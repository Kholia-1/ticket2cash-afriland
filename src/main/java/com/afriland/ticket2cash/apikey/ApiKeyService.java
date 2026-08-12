package com.afriland.ticket2cash.apikey;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();
    private final ApiKeyRepository repository;

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /** Generates a new secret key like t2c_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX */
    public String generateRawKey() {
        StringBuilder sb = new StringBuilder("t2c_");
        for (int i = 0; i < 32; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    public String hash(String rawKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash API key", e);
        }
    }

    /** Returns the ApiKey if the raw key is valid and active; records usage. */
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        Optional<ApiKey> found = repository.findByKeyHash(hash(rawKey.trim()));
        if (found.isEmpty() || !found.get().isActive()) return Optional.empty();
        ApiKey k = found.get();
        k.setLastUsedAt(LocalDateTime.now());
        k.setCallCount((k.getCallCount() == null ? 0L : k.getCallCount()) + 1);
        repository.save(k);
        return Optional.of(k);
    }
}
