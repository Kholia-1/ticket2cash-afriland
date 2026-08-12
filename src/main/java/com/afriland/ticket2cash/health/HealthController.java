package com.afriland.ticket2cash.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "application", "Ticket2Cash",
                "status", "RUNNING",
                "version", "1.0.0",
                "module", "Cashback Supermarches"
        );
    }
}