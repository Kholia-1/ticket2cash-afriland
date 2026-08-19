package com.afriland.ticket2cash.audit;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public, deliberately small representation of an audit event. */
public class AuditLogResponse {
    private Long id;
    private String action;
    private String actionLabel;
    private String moduleName;
    private String category;
    private String entityType;
    private Long entityId;
    private String businessReference;
    private String actor;
    private String status;
    private String statusLabel;
    private String message;
    private String details;
    private LocalDateTime createdAt;

    public static AuditLogResponse from(AuditLog source) {
        AuditLogResponse out = new AuditLogResponse();
        out.id = source.getId();
        out.action = source.getAction();
        out.actionLabel = actionLabel(source.getAction());
        out.moduleName = source.getModuleName();
        out.category = category(source.getAction());
        out.entityType = source.getEntityType();
        out.entityId = source.getEntityId();
        out.businessReference = businessReference(source.getEntityId(), source.getMessage());
        out.actor = safe(source.getActor());
        out.status = source.getStatus();
        out.statusLabel = statusLabel(source.getStatus());
        out.message = sanitize(source.getMessage());
        out.details = out.message;
        out.createdAt = source.getCreatedAt();
        return out;
    }

    private static String businessReference(Long entityId, String message) {
        if (entityId != null) return String.valueOf(entityId);
        if (message == null) return null;
        Matcher m = Pattern.compile("\\b(?:TXN|PAY|CREDIT|CLAIM|CAMP|MERCH)-[A-Za-z0-9][A-Za-z0-9_-]*\\b", Pattern.CASE_INSENSITIVE).matcher(message);
        return m.find() ? m.group() : null;
    }

    static String sanitize(String value) {
        if (value == null) return null;
        String s = value.replaceAll("(?i)(cardHash|cardNumber|pan|password|passwd|pwd|token|secret|api[-_ ]?key|bearer|privateKey)\\s*[:=]\\s*[^|,; ]+", "$1=[MASQUÉ]");
        s = s.replaceAll("(?<!\\d)(?:\\d[ -]?){12,19}(?!\\d)", "[MASQUÉ]");
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }

    private static String safe(String value) { return value == null || value.isBlank() ? null : value; }
    private static String statusLabel(String value) {
        if (value == null || value.isBlank()) return "Non renseigné";
        return switch (value.toUpperCase()) { case "SUCCESS", "APPROVED" -> "Succès"; case "FAILED", "REJECTED" -> "Échec / Rejeté"; case "PENDING", "STARTED" -> "En attente"; default -> value; };
    }
    private static String category(String action) {
        String a = action == null ? "" : action.toUpperCase();
        if (a.contains("LOGIN") || a.contains("PASSWORD") || a.contains("USER")) return "Connexion / Administration";
        if (a.contains("CSV") || a.contains("IMPORT")) return "Import";
        if (a.contains("CREDIT")) return "Crédit client";
        if (a.contains("PAYMENT") || a.contains("CASHBACK")) return "Paiement";
        if (a.contains("TRANSACTION") || a.contains("PREPAYMENT")) return "Transaction";
        if (a.contains("CLAIM")) return "Réclamation";
        if (a.contains("LOYALTY") || a.contains("REWARD") || a.contains("TIER")) return "Fidélité";
        if (a.contains("FRAUD")) return "Fraude";
        if (a.contains("AUDIT")) return "Audit";
        return "Administration";
    }
    private static String actionLabel(String action) {
        if (action == null) return "Non renseigné";
        return switch (action) {
            case "LOGIN_SUCCESS" -> "Connexion réussie"; case "LOGIN_FAILED" -> "Échec de connexion"; case "LOGOUT" -> "Déconnexion";
            case "IMPORT_CARD_TRANSACTIONS_CSV" -> "Import transactions cartes"; case "TRANSACTION_CSV_IMPORT_COMPLETED" -> "Import transactions terminé";
            case "TRANSACTION_CSV_INSERTED" -> "Transaction importée"; case "TRANSACTION_CSV_DUPLICATE" -> "Doublon transaction détecté";
            case "TRANSACTION_MANUAL_REVIEW" -> "Transaction mise en révision"; case "TRANSACTION_REJECTED" -> "Transaction rejetée";
            case "TRANSACTION_APPROVED_FOR_PAYMENT" -> "Transaction approuvée pour paiement"; case "TRANSACTION_APPROVED_FOR_CREDIT" -> "Transaction approuvée pour crédit";
            case "PROCESS_PENDING_CASHBACK_PAYMENTS" -> "Traitement paiements en attente"; case "CASHBACK_PAYMENT_PAID" -> "Paiement cashback traité";
            case "CASHBACK_CREDITED" -> "Cashback crédité au client"; case "CASHBACK_CREDIT_FAILED" -> "Échec crédit cashback";
            default -> action;
        };
    }

    public Long getId(){return id;} public String getAction(){return action;} public String getActionLabel(){return actionLabel;}
    public String getModuleName(){return moduleName;} public String getCategory(){return category;} public String getEntityType(){return entityType;}
    public Long getEntityId(){return entityId;} public String getBusinessReference(){return businessReference;} public String getActor(){return actor;}
    public String getStatus(){return status;} public String getStatusLabel(){return statusLabel;} public String getMessage(){return message;}
    public String getDetails(){return details;} public LocalDateTime getCreatedAt(){return createdAt;}
    /** Backward-compatible aliases consumed by older frontend widgets. */
    public String getIdentifier(){return businessReference;}
    public String getTimestamp(){return createdAt == null ? null : createdAt.toString();}
}
