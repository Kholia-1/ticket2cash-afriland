package com.afriland.ticket2cash.points;

/** Type of ledger movement on a points account. */
public enum PointsTransactionType {

    /** Points earned from a qualifying transaction, bonus, or gamification win. */
    EARN,

    /** Points spent by the customer (redemption). */
    BURN,

    /** Points that expired without being burned. */
    EXPIRE,

    /** Manual adjustment by admin (positive or negative). */
    ADJUST
}
