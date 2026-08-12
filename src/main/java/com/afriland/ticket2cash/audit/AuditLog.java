package com.afriland.ticket2cash.audit;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;
    private String moduleName;
    private String entityType;
    private Long entityId;
    private String actor;
    private String status;

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDateTime createdAt;

    public AuditLog() {
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getModuleName() { return moduleName; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getActor() { return actor; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setAction(String action) { this.action = action; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public void setActor(String actor) { this.actor = actor; }
    public void setStatus(String status) { this.status = status; }
    public void setMessage(String message) { this.message = message; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}