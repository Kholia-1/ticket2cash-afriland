package com.afriland.ticket2cash.card;

public enum PrepaidCardStatus {

    /** Card is active and can be used. */
    ACTIVE,

    /** Card has been blocked (fraud, customer request). Can be unblocked. */
    BLOCKED,

    /** Card has passed its physical expiration date. */
    EXPIRED,

    /** Card has been permanently cancelled. */
    CANCELLED,

    /** Card has been issued but not yet activated by the customer. */
    PENDING_ACTIVATION
}
