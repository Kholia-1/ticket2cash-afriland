package com.afriland.ticket2cash.campaign;

/**
 * Merchant Category Code (MCC) — 4-digit code used by card networks (Visa,
 * Mastercard) to classify merchants by business type.
 *
 * <p>This is a curated shortlist of the 30 most common MCCs relevant to the
 * Cameroonian retail cashback context. The full ISO 18245 catalog has ~1000
 * codes; the extra 970 are rare enough that we treat them as "OTHER".
 *
 * <p>Used by {@code CampaignTriggerType.MERCHANT_TRANSACTION} campaigns to
 * filter which transactions qualify. Example: a campaign with {@code MCC_5411}
 * (supermarkets) only credits cashback on grocery transactions, ignoring
 * everything else at the same merchant.
 */
public enum MccCode {

    // ---------------- Retail (5xxx) ----------------
    MCC_5411("5411", "Supermarchés / Épiceries"),
    MCC_5541("5541", "Stations-service"),
    MCC_5812("5812", "Restaurants"),
    MCC_5813("5813", "Bars et lounges"),
    MCC_5814("5814", "Restauration rapide"),
    MCC_5912("5912", "Pharmacies / Drugstores"),
    MCC_5921("5921", "Vente d'alcool"),
    MCC_5941("5941", "Articles de sport"),
    MCC_5942("5942", "Librairies"),
    MCC_5945("5945", "Jeux, jouets"),
    MCC_5947("5947", "Cadeaux, cartes de vœux"),
    MCC_5977("5977", "Cosmétique et parfumerie"),
    MCC_5992("5992", "Fleuristes"),
    MCC_5999("5999", "Commerce de détail divers"),

    // ---------------- Cash / Banking (6xxx) ----------------
    MCC_6011("6011", "Retrait DAB (cash)"),
    MCC_6300("6300", "Assurances"),

    // ---------------- Services (7xxx) ----------------
    MCC_7011("7011", "Hôtels / Hébergement"),
    MCC_7230("7230", "Salons de beauté / Coiffure"),
    MCC_7538("7538", "Réparation automobile"),
    MCC_7801("7801", "Loteries"),
    MCC_7995("7995", "Casinos / Jeux d'argent"),

    // ---------------- Health (8xxx) ----------------
    MCC_8062("8062", "Hôpitaux"),
    MCC_8099("8099", "Services médicaux divers"),

    // ---------------- Transport / Utilities (4xxx) ----------------
    MCC_4111("4111", "Transport local"),
    MCC_4121("4121", "Taxis"),
    MCC_4511("4511", "Compagnies aériennes"),
    MCC_4722("4722", "Agences de voyage"),
    MCC_4814("4814", "Télécoms / Factures téléphone"),
    MCC_4900("4900", "Utilities (eau, électricité)"),

    // ---------------- Tech / Electronics (5xxx) ----------------
    MCC_5045("5045", "Informatique et périphériques"),
    MCC_5732("5732", "Électronique grand public"),

    // ---------------- Catch-all ----------------
    OTHER("OTHER", "Autre / Non catégorisé");

    private final String code;
    private final String label;

    MccCode(String code, String label) { this.code = code; this.label = label; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
}
