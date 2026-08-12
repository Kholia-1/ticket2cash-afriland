package com.afriland.ticket2cash.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Central validation helpers used by every controller that accepts user input.
 * Each method returns {@code null} when the value is valid, or a human-readable
 * French error message otherwise.
 *
 * <p>Design goal: consistent error messages across all endpoints, easy to translate
 * later, and impossible to bypass by adding a new controller (developer sees the
 * shared helper and uses it).
 */
public final class ValidationUtils {

    /** At least one Unicode letter (Latin, accented, etc.). */
    private static final Pattern HAS_LETTER = Pattern.compile(".*[\\p{L}].*");

    /** Standard RFC-5322-lite email pattern — good enough for real use. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    /** Username: letters, digits, dot, underscore, dash. 3-40 chars.
     *  Also accepts an email as username. */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]{3,40}$");

    /** Cameroon phone (+237 xxxxxxxxx) or generic international. Very permissive. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s.\\-]{7,20}$");

    /** Bank account number: 10-30 digits/letters. */
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9]{5,30}$");

    /** Credit card number: 12-19 digits (Luhn not checked here — done elsewhere if needed). */
    private static final Pattern CARD_PATTERN = Pattern.compile("^[0-9]{12,19}$");

    /** SKU: alphanumeric + dash/underscore/dot, 1-60 chars. */
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]{1,60}$");

    private ValidationUtils() {}

    // -------------------------------------------------------------------- names

    /**
     * Real-name validation. Applies to product name, merchant name, brand name,
     * campaign name, rule name, and client full name. Rejects things like "@",
     * "?", "1", "42" — anything that isn't a plausible name.
     */
    public static String validateName(String raw, String fieldLabel) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Le " + fieldLabel + " est requis";
        }
        String t = raw.trim();
        if (t.length() < 2) {
            return "Le " + fieldLabel + " doit contenir au moins 2 caractères";
        }
        if (t.length() > 200) {
            return "Le " + fieldLabel + " ne doit pas dépasser 200 caractères";
        }
        if (!HAS_LETTER.matcher(t).matches()) {
            return "Le " + fieldLabel + " doit contenir au moins une lettre (ex: 'Santa Lucia', pas '1' ou '@')";
        }
        return null;
    }

    /** Optional name — {@code null} or blank means "not provided", still valid. */
    public static String validateOptionalName(String raw, String fieldLabel) {
        if (raw == null || raw.trim().isEmpty()) return null;
        return validateName(raw, fieldLabel);
    }

    // -------------------------------------------------------------------- description / free text

    public static String validateDescription(String raw, String fieldLabel, int maxLen) {
        if (raw == null || raw.trim().isEmpty()) return null;   // optional
        if (raw.length() > maxLen) return "La " + fieldLabel + " ne doit pas dépasser " + maxLen + " caractères";
        return null;
    }

    // -------------------------------------------------------------------- account & card

    public static String validateAccountNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Le numéro de compte est requis";
        if (!ACCOUNT_PATTERN.matcher(raw.trim()).matches()) {
            return "Le numéro de compte doit contenir 5 à 30 caractères alphanumériques (pas d'espace)";
        }
        return null;
    }

    public static String validateCardNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null; // optional
        String digits = raw.trim().replace(" ", "").replace("-", "");
        if (!CARD_PATTERN.matcher(digits).matches()) {
            return "Le numéro de carte doit contenir 12 à 19 chiffres";
        }
        return null;
    }

    // -------------------------------------------------------------------- SKU

    public static String validateSku(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null; // optional
        if (!SKU_PATTERN.matcher(raw.trim()).matches()) {
            return "Le SKU doit contenir uniquement lettres/chiffres/._- (60 caractères max)";
        }
        return null;
    }

    // -------------------------------------------------------------------- contact

    public static String validateEmail(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null; // optional in most contexts
        if (!EMAIL_PATTERN.matcher(raw.trim()).matches()) {
            return "L'email n'est pas au format valide (ex: nom@domaine.com)";
        }
        return null;
    }

    public static String validateRequiredEmail(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "L'email est requis";
        return validateEmail(raw);
    }

    public static String validatePhone(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null; // optional
        if (!PHONE_PATTERN.matcher(raw.trim()).matches()) {
            return "Le numéro de téléphone doit contenir 7 à 20 chiffres (ex: +237 6XX XX XX XX)";
        }
        return null;
    }

    // -------------------------------------------------------------------- login

    public static String validateUsername(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Le nom d'utilisateur est requis";
        String t = raw.trim();
        // Accept if it matches USERNAME_PATTERN OR if it's a valid email
        if (USERNAME_PATTERN.matcher(t).matches()) return null;
        if (EMAIL_PATTERN.matcher(t).matches()) return null;
        return "Le nom d'utilisateur doit contenir 3-40 caractères (lettres, chiffres, . _ -) ou être une adresse email";
    }

    public static String validatePassword(String raw) {
        if (raw == null || raw.isEmpty()) return "Le mot de passe est requis";
        if (raw.length() < 8) return "Le mot de passe doit contenir au moins 8 caractères";
        return null;
    }

    // -------------------------------------------------------------------- money & percent

    public static String validatePositiveOrZero(BigDecimal v, String fieldLabel) {
        if (v == null) return null; // optional
        if (v.signum() < 0) return "Le " + fieldLabel + " doit être positif ou nul";
        return null;
    }

    public static String validatePositive(BigDecimal v, String fieldLabel) {
        if (v == null) return null;
        if (v.signum() <= 0) return "Le " + fieldLabel + " doit être strictement positif";
        return null;
    }

    public static String validatePercentage(BigDecimal v, String fieldLabel) {
        if (v == null) return null;
        if (v.signum() < 0) return "Le " + fieldLabel + " doit être ≥ 0";
        if (v.compareTo(BigDecimal.valueOf(100)) > 0) return "Le " + fieldLabel + " doit être ≤ 100";
        return null;
    }

    // -------------------------------------------------------------------- dates

    public static String validateDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) return null; // both optional
        if (end.isBefore(start)) return "La date de fin doit être postérieure ou égale à la date de début";
        return null;
    }

    // -------------------------------------------------------------------- integer counts

    public static String validateNonNegativeCount(Integer v, String fieldLabel) {
        if (v == null) return null;
        if (v < 0) return "Le " + fieldLabel + " doit être ≥ 0";
        return null;
    }
}
