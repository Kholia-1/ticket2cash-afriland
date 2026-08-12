package com.afriland.ticket2cash.settings;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SystemSettingService {

    private final SystemSettingRepository repository;

    public SystemSettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    public SystemSetting createIfMissing(String key, String value, String category, String description) {
        return repository.findBySettingKey(key).orElseGet(() -> {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setCategory(category);
            setting.setDescription(description);
            return repository.save(setting);
        });
    }

    public SystemSetting updateValue(String key, String value) {
        SystemSetting setting = repository.findBySettingKey(key).orElseGet(() -> {
            SystemSetting newSetting = new SystemSetting();
            newSetting.setSettingKey(key);
            newSetting.setCategory("GENERAL");
            newSetting.setDescription("Setting created from API");
            return newSetting;
        });

        setting.setSettingValue(value);
        return repository.save(setting);
    }

    public String getString(String key, String defaultValue) {
        return repository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElse(defaultValue);
    }

    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
        try {
            return repository.findBySettingKey(key)
                    .map(setting -> new BigDecimal(setting.getSettingValue()))
                    .orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public Integer getInteger(String key, Integer defaultValue) {
        try {
            return repository.findBySettingKey(key)
                    .map(setting -> Integer.parseInt(setting.getSettingValue()))
                    .orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        try {
            return repository.findBySettingKey(key)
                    .map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
                    .orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void initDefaults() {
        createIfMissing("TICKET_SIMULATED_AMOUNT", "44500", "OCR", "Default simulated ticket amount");
        createIfMissing("DEFAULT_CURRENCY", "FCFA", "GENERAL", "Default application currency");
        createIfMissing("FRAUD_NORMAL_SCORE", "20", "FRAUD", "Normal fraud score for accepted ticket");
        createIfMissing("FRAUD_DUPLICATE_SCORE", "95", "FRAUD", "Fraud score for duplicate ticket");
        createIfMissing("ANTI_FRAUD_ENABLED", "true", "FRAUD", "Enable or disable duplicate ticket detection");
        createIfMissing("DEFAULT_ACTOR", "ADMIN_DEMO", "AUDIT", "Default actor for audit logs");
    }
}