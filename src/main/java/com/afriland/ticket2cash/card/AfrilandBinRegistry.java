package com.afriland.ticket2cash.card;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Placeholder registry of Afriland prepaid card BINs (Bank Identification
 * Numbers — first 6 digits of a card number).
 *
 * <p><b>These are placeholder values.</b> When the bank provides the real BINs
 * assigned to Afriland prepaid card products, replace the values below.
 *
 * <p>BINs let campaigns target specific card products — for example a CLO
 * offer restricted to "Afriland Prepaid Gold" cards or a cashback campaign
 * limited to youth prepaid cards.
 */
public final class AfrilandBinRegistry {

    private AfrilandBinRegistry() {}

    /** Placeholder BIN ranges — 6-digit prefixes. Replace with the real ones. */
    public static final List<BinEntry> BINS = List.of(
            new BinEntry("456120", "Afriland Prépayée Classique", "Carte prépayée standard grand public"),
            new BinEntry("456121", "Afriland Prépayée Gold",       "Segment premium — plafond élevé"),
            new BinEntry("456122", "Afriland Prépayée Jeunes",     "Cible 18-25 ans, tarifs préférentiels"),
            new BinEntry("456123", "Afriland Prépayée Corporate",  "Cartes prépayées entreprise / paie"),
            new BinEntry("456124", "Afriland Prépayée Cadeau",     "Carte cadeau prépayée non-rechargeable")
    );

    public static class BinEntry {
        public final String bin;
        public final String productName;
        public final String description;

        public BinEntry(String bin, String productName, String description) {
            this.bin = bin;
            this.productName = productName;
            this.description = description;
        }

        public String getBin() { return bin; }
        public String getProductName() { return productName; }
        public String getDescription() { return description; }
    }

    /** For UI dropdowns: {bin, label} pairs. */
    public static List<java.util.Map<String, String>> asOptions() {
        return BINS.stream()
                .map(b -> java.util.Map.of("bin", b.bin, "label", b.bin + " — " + b.productName))
                .collect(Collectors.toList());
    }
}
