package com.afriland.ticket2cash.voucher;

public enum VoucherStatus {

    /** Issued to the customer, not yet used. */
    ISSUED,

    /** Fully consumed (single-use, or multi-use with all uses exhausted). */
    CONSUMED,

    /** Past validTo without being fully consumed. */
    EXPIRED,

    /** Admin cancelled before consumption. */
    CANCELLED
}
