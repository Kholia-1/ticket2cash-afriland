package com.afriland.ticket2cash.points;

/**
 * The origin of a points ledger entry. Used for reporting: which channels
 * bring the most engagement.
 */
public enum PointsSource {

    /** Standard earn from a matching loyalty transaction. */
    LOYALTY_TRANSACTION,

    /** Bonus tied to a specific merchant campaign. */
    MERCHANT_BONUS,

    /** Bonus tied to an MCC (category-based earn). */
    MCC_BONUS,

    /** Bonus tied to a specific product SKU. */
    PRODUCT_BONUS,

    /** Points earned via a gamification win (future GL-05). */
    GAMIFICATION,

    /** Points credited via a Card-Linked Offer redemption (GL-04). */
    CLO_REDEMPTION,

    /** Points redeemed via marketplace (future GL-02b). */
    MARKETPLACE_REDEMPTION,

    /** Points exchanged to another programme (future GL-03). */
    PROGRAM_EXCHANGE,

    /** Manual credit or debit by an admin. */
    MANUAL_ADJUST,

    /** Points that reached their expiration date without being burned. */
    EXPIRATION_SWEEP,

    /** Welcome bonus at account activation. */
    WELCOME_BONUS
}
