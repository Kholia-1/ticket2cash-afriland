package com.afriland.ticket2cash.settings;

import com.afriland.ticket2cash.audit.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SystemSettingController {

    private final SystemSettingRepository repository;
    private final SystemSettingService service;
    private final AuditLogService auditLogService;

    public SystemSettingController(SystemSettingRepository repository,
                                   SystemSettingService service,
                                   AuditLogService auditLogService) {
        this.repository = repository;
        this.service = service;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<SystemSetting> getAllSettings() {
        return repository.findAll();
    }

    @GetMapping("/category/{category}")
    public List<SystemSetting> getSettingsByCategory(@PathVariable String category) {
        return repository.findByCategory(category);
    }

    @GetMapping("/{settingKey}")
    public ResponseEntity<SystemSetting> getSettingByKey(@PathVariable String settingKey) {
        return repository.findBySettingKey(settingKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{settingKey}")
    public SystemSetting updateSetting(
            @PathVariable String settingKey,
            @RequestParam String value
    ) {
        SystemSetting setting = service.updateValue(settingKey, value);

        auditLogService.log(
                "UPDATE_SETTING",
                "SETTINGS",
                "SystemSetting",
                setting.getId(),
                service.getString("DEFAULT_ACTOR", "ADMIN_DEMO"),
                "SUCCESS",
                "Setting updated: " + settingKey + " = " + value
        );

        return setting;
    }

    @PostMapping("/init-defaults")
    public String initDefaults() {
        service.initDefaults();
        return "Default settings initialized";
    }
}