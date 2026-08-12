package com.afriland.ticket2cash.voucher;

/**
 * The 5 typologies of voucher campaigns from GL-06.
 */
public enum VoucherType {

    /** Progressive loyalty: earned after N purchases or X FCFA cumulative spend. */
    PROGRESSIVE_LOYALTY,

    /** One-shot: distributed immediately (e.g. signup promo). */
    ONE_SHOT,

    /** Welcome: triggered when the client activates their card. */
    WELCOME,

    /** Birthday: triggered on the client's anniversary date. */
    BIRTHDAY,

    /** Partner campaign: targeted marketing campaign for a specific merchant. */
    PARTNER_CAMPAIGN
}
