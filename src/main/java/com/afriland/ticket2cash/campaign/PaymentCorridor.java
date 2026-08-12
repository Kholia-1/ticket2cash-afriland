package com.afriland.ticket2cash.campaign;

/**
 * Geographic corridor of a transaction — where the merchant is located relative
 * to the cardholder's country. Used by campaign filters to restrict cashback
 * to specific corridors (e.g. "5% cashback only on domestic transactions").
 */
public enum PaymentCorridor {

    /** All corridors (default, no filter). */
    ALL,

    /** Domestic — both cardholder and merchant are in Cameroon. */
    DOMESTIC,

    /** International — merchant is outside Cameroon. */
    INTERNATIONAL,

    /** Cross-border — CEMEA region excluding Cameroon (regional). */
    CROSS_BORDER
}
