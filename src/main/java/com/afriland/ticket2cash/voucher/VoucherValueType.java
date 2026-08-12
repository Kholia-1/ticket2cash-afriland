package com.afriland.ticket2cash.voucher;

public enum VoucherValueType {

    /** Percentage discount (e.g. 15% off). */
    PERCENTAGE_DISCOUNT,

    /** Fixed FCFA amount off (e.g. 5000 FCFA off). */
    FIXED_AMOUNT,

    /** Free product / gift (numeric value ignored). */
    FREE_PRODUCT
}
