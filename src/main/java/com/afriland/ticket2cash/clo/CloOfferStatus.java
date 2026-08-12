package com.afriland.ticket2cash.clo;

public enum CloOfferStatus {
    /** Being edited, not visible to cardholders yet. */
    DRAFT,
    /** Live and being served to opted-in cardholders. */
    ACTIVE,
    /** Temporarily suspended without deletion. */
    PAUSED,
    /** Past validTo. */
    EXPIRED,
    /** Removed from catalog. */
    ARCHIVED
}
