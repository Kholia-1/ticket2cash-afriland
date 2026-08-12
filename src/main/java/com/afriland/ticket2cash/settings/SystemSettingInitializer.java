package com.afriland.ticket2cash.settings;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SystemSettingInitializer implements CommandLineRunner {

    private final SystemSettingService service;

    public SystemSettingInitializer(SystemSettingService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        service.initDefaults();
    }
}