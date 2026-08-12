package com.afriland.ticket2cash.loyalty;

public enum LoyaltyResultStatus {
    /** Amount computed; waiting for batch approval. */
    PENDING,
    /** Batch approved; queued for Core Banking credit. */
    QUEUED,
    /** Credit successful on the client's card / account. */
    CREDITED,
    /** Credit failed at Core Banking (e.g. account frozen). */
    FAILED,
    /** Batch was cancelled before crediting — no payment. */
    CANCELLED
}
