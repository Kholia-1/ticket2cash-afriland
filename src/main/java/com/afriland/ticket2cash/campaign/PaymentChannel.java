package com.afriland.ticket2cash.campaign;

/**
 * Payment channel through which a transaction was initiated. Used by campaign
 * filters to restrict cashback to specific channels (e.g. "only mobile wallet
 * transactions get 5% cashback").
 */
public enum PaymentChannel {

    /** All channels (default, no filter). */
    ALL,

    /** E-commerce — card entered on a website. */
    ECOMM,

    /** Face-to-face — physical card used in a physical store. */
    F2F,

    /** Mobile wallet — Apple Pay, Google Pay, Samsung Pay, etc. */
    MOBILE_WALLET,

    /** Card Not Present — phone, mail, or recurring subscription. */
    CNP,

    /** ATM withdrawal. */
    ATM
}
